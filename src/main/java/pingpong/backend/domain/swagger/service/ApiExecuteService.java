package pingpong.backend.domain.swagger.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional(readOnly = true)
public class ApiExecuteService {

	private final EndpointRepository endpointRepository;
	private final TeamService teamService;
	private final SwaggerParameterRepository swaggerParameterRepository;
	private final SwaggerRequestRepository swaggerRequestRepository;
	private final SwaggerUrlResolver swaggerUrlResolver;
	private final ApiExecuteClient apiExecuteClient;
	private final ObjectMapper objectMapper;

	public ApiExecuteResponse execute(Long endpointId, Long teamId, ApiExecuteRequest req) {
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

		// 4. required 파라미터 검증
		validateParameters(endpointId, req);

		// 5. required body 검증
		validateRequestBody(endpointId, req);

		// 6. base URL 추출
		String baseUrl = swaggerUrlResolver.resolveBaseUrl(team.getSwagger());

		// 7. path variable 치환 + full URL 빌드
		Map<String, String> pathVars = req.pathVariables() != null ? req.pathVariables() : new HashMap<>();
		String resolvedPath = UriComponentsBuilder.fromPath(endpoint.getPath())
			.buildAndExpand(pathVars)
			.toUriString();
		String fullUrl = baseUrl + resolvedPath;

		// 8. 외부 API 요청
		ResponseEntity<String> response = apiExecuteClient.execute(
			fullUrl,
			endpoint.getMethod(),
			req.queryParams(),
			req.headers(),
			req.body()
		);

		// 9. 응답 변환
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
