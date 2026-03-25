package pingpong.backend.domain.swagger.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.SwaggerParameter;
import pingpong.backend.domain.swagger.SwaggerRequest;
import pingpong.backend.domain.swagger.client.ApiExecuteClient;
import pingpong.backend.domain.swagger.dto.request.ApiExecuteRequest;
import pingpong.backend.domain.swagger.dto.response.ApiExecuteResponse;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.swagger.repository.SwaggerParameterRepository;
import pingpong.backend.domain.swagger.repository.SwaggerRequestRepository;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.service.TeamService;
import pingpong.backend.global.exception.CustomException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, noRollbackFor = CustomException.class)
public class ApiExecuteService {

	private final EndpointRepository endpointRepository;
	private final TeamService teamService;
	private final SwaggerParameterRepository swaggerParameterRepository;
	private final SwaggerRequestRepository swaggerRequestRepository;
	private final SwaggerUrlResolver swaggerUrlResolver;
	private final SsrfGuard ssrfGuard;
	private final ApiExecuteClient apiExecuteClient;
	private final ObjectMapper objectMapper;

	public ApiExecuteResponse execute(Long endpointId, Long teamId, ApiExecuteRequest req, String proxyAuthorization) {
		// 1. Endpoint 조회
		Endpoint endpoint = endpointRepository.findById(endpointId)
			.orElseThrow(() -> new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND));

		// 2. Team 조회
		Team team = teamService.getTeam(teamId);

		// 3. 팀-엔드포인트 소속 검증
		Long endpointTeamId = endpoint.getSnapshot().getTeam().getId();
		if (!endpointTeamId.equals(teamId)) {
			throw new CustomException(SwaggerErrorCode.ENDPOINT_TEAM_MISMATCH);
		}

		// 4. X-Proxy-Authorization를 headers에 머지 (항상 우선)
		Map<String, String> mergedHeaders = new HashMap<>(req.headers() != null ? req.headers() : new HashMap<>());
		if (proxyAuthorization != null && !proxyAuthorization.isBlank()) {
			mergedHeaders.keySet().removeIf(k -> k.equalsIgnoreCase("authorization"));
			String authValue = proxyAuthorization.startsWith("Bearer ") ? proxyAuthorization : "Bearer " + proxyAuthorization;
			mergedHeaders.put("Authorization", authValue);
		}
		ApiExecuteRequest mergedReq = new ApiExecuteRequest(req.pathVariables(), req.queryParams(), mergedHeaders, req.body());

		// 5. required 파라미터 검증
		validateParameters(endpointId, mergedReq);

		// 6. required body 검증
		validateRequestBody(endpointId, mergedReq);

		// 7. base URL 추출 및 SSRF 차단
		String baseUrl = swaggerUrlResolver.resolveBaseUrl(team.getSwagger());
		ssrfGuard.validate(baseUrl);

		// 8. path variable 치환 + full URL 빌드
		Map<String, String> pathVars = mergedReq.pathVariables() != null ? mergedReq.pathVariables() : new HashMap<>();
		String resolvedPath = UriComponentsBuilder.fromPath(endpoint.getPath())
			.buildAndExpand(pathVars)
			.toUriString();
		String fullUrl = baseUrl + resolvedPath;

		// 9. 외부 API 요청
		ResponseEntity<String> response;
		try {
			response = apiExecuteClient.execute(
				fullUrl,
				endpoint.getMethod(),
				mergedReq.queryParams(),
				mergedReq.headers(),
				mergedReq.body()
			);
		} catch (HttpStatusCodeException e) {
			//서버가 응답을 준 모든 에러를 여기서 잡음
			Map<String, String> errorHeaders = new HashMap<>();
			if (e.getResponseHeaders() != null) {
				e.getResponseHeaders().forEach((k, v) -> errorHeaders.put(k, v.get(0)));
			}

			return new ApiExecuteResponse(
				e.getStatusCode().value(),
				errorHeaders,
				parseBody(e.getResponseBodyAsString())
			);
		}catch(Exception e){
			//시스템 에러이므로 진짜 예외처리
			log.error("API 접속 실패:{}",e.getMessage());
			throw new CustomException(SwaggerErrorCode.API_EXECUTE_ERROR);
		}
		// 10. 정상 200번대 응답 변환
		Map<String, String> responseHeaders = new HashMap<>();
		response.getHeaders().forEach((key, values) -> {
			if (!values.isEmpty()) {
				responseHeaders.put(key, values.getFirst());
			}
		});

		Object responseBody = parseBody(response.getBody());

		return new ApiExecuteResponse(
			response.getStatusCode().value(),
			responseHeaders,
			responseBody
		);
	}

	private void validateParameters(Long endpointId, ApiExecuteRequest req) {
		List<SwaggerParameter> parameters = swaggerParameterRepository.findByEndpointId(endpointId);
		for (SwaggerParameter param : parameters) {
			if (!Boolean.TRUE.equals(param.getRequired())) {
				continue;
			}
			String name = param.getName();
			String inType = param.getInType();
			if (inType == null || "cookie".equalsIgnoreCase(inType)) {
				continue;
			}

			boolean missing = switch (inType.toLowerCase()) {
				case "path" -> req.pathVariables() == null || !req.pathVariables().containsKey(name);
				case "query" -> req.queryParams() == null || !req.queryParams().containsKey(name);
				case "header" -> req.headers() == null || !req.headers().containsKey(name);
				default -> false;
			};

			if (missing) {
				log.warn("Missing required parameter: {} ({})", name, inType);
				throw new CustomException(SwaggerErrorCode.MISSING_REQUIRED_PARAMETER);
			}
		}
	}

	private void validateRequestBody(Long endpointId, ApiExecuteRequest req) {
		List<SwaggerRequest> requests = swaggerRequestRepository.findByEndpointId(endpointId);
		for (SwaggerRequest swaggerRequest : requests) {
			if (swaggerRequest.isRequired() && req.body() == null) {
				throw new CustomException(SwaggerErrorCode.MISSING_REQUEST_BODY);
			}
		}
	}

	private Object parseBody(String rawBody) {
		if (rawBody == null || rawBody.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readTree(rawBody);
		} catch (JsonProcessingException e) {
			return rawBody;
		}
	}
}
