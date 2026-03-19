package pingpong.backend.domain.qa.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.qa.QaCase;
import pingpong.backend.domain.qa.QaErrorCode;
import pingpong.backend.domain.qa.QaExecuteResult;
import pingpong.backend.domain.qa.QaSyncHistory;
import pingpong.backend.domain.qa.dto.EndpointQaSummaryResponse;
import pingpong.backend.domain.qa.dto.EndpointQaTagGroupResponse;
import pingpong.backend.domain.qa.dto.EndpointRequestBodyDto;
import pingpong.backend.domain.qa.dto.EndpointResponseDto;
import pingpong.backend.domain.qa.dto.EndpointSecurityDto;
import pingpong.backend.domain.qa.dto.QaCaseDetailDto;
import pingpong.backend.domain.qa.dto.QaCaseSummaryDto;
import pingpong.backend.domain.qa.dto.QaExecuteResultDto;
import pingpong.backend.domain.qa.dto.QaScenarioDetail;
import pingpong.backend.domain.qa.dto.QaScenarioResponse;
import pingpong.backend.domain.qa.dto.QaTeamFailureResponse;
import pingpong.backend.domain.qa.repository.QaSyncHistoryRepository;
import pingpong.backend.domain.swaggerdiff.dto.EndpointParameterDto;
import pingpong.backend.domain.qa.repository.QaCaseRepository;
import pingpong.backend.domain.qa.repository.QaExecuteResultRepository;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.dto.EndpointAggregate;
import pingpong.backend.domain.swagger.dto.request.ApiExecuteRequest;
import pingpong.backend.domain.swagger.dto.response.ApiExecuteResponse;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.swagger.repository.SwaggerEndpointSecurityRepository;
import pingpong.backend.domain.swagger.repository.SwaggerParameterRepository;
import pingpong.backend.domain.swagger.repository.SwaggerRequestRepository;
import pingpong.backend.domain.swagger.repository.SwaggerResponseRepository;
import pingpong.backend.domain.swagger.repository.SwaggerSnapshotRepository;
import pingpong.backend.domain.swagger.service.ApiExecuteService;
import pingpong.backend.global.exception.CustomException;

@Service
@Slf4j
@Transactional(readOnly = true)
public class QaService {


	private final QaCaseRepository qaCaseRepository;
	private final QaExecuteResultRepository qaExecuteResultRepository;
	private final ApiExecuteService apiExecuteService;
	private final ObjectMapper objectMapper;
	private final SwaggerSnapshotRepository swaggerSnapshotRepository;
	private final EndpointRepository endpointRepository;
	private final SwaggerEndpointSecurityRepository swaggerEndpointSecurityRepository;
	private final SwaggerParameterRepository swaggerParameterRepository;
	private final SwaggerRequestRepository swaggerRequestRepository;
	private final SwaggerResponseRepository swaggerResponseRepository;
	private final LlmQaService llmQaService;
	private final QaSyncHistoryRepository qaSyncHistoryRepository;


	public QaService(QaCaseRepository qaCaseRepository,
		QaExecuteResultRepository qaExecuteResultRepository, ApiExecuteService apiExecuteService,
		ObjectMapper objectMapper, SwaggerSnapshotRepository swaggerSnapshotRepository,
		EndpointRepository endpointRepository, SwaggerEndpointSecurityRepository swaggerEndpointSecurityRepository,
		SwaggerParameterRepository swaggerParameterRepository, SwaggerRequestRepository swaggerRequestRepository,
		SwaggerResponseRepository swaggerResponseRepository, LlmQaService llmQaService, QaSyncHistoryRepository qaSyncHistoryRepository) {
		this.qaCaseRepository = qaCaseRepository;
		this.qaExecuteResultRepository = qaExecuteResultRepository;
		this.apiExecuteService = apiExecuteService;
		this.objectMapper = objectMapper;
		this.swaggerSnapshotRepository = swaggerSnapshotRepository;
		this.endpointRepository = endpointRepository;
		this.swaggerEndpointSecurityRepository = swaggerEndpointSecurityRepository;
		this.swaggerParameterRepository = swaggerParameterRepository;
		this.swaggerRequestRepository = swaggerRequestRepository;
		this.swaggerResponseRepository = swaggerResponseRepository;
		this.llmQaService = llmQaService;
		this.qaSyncHistoryRepository=qaSyncHistoryRepository;
	}

	public List<QaCaseSummaryDto> getQaCasesByEndpointId(Long endpointId) {

		Endpoint endpoint = endpointRepository.findById(endpointId)
			.orElseThrow(() -> new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND));

		return qaCaseRepository.findAllByEndpointId(endpointId).stream()
			.map(qa -> new QaCaseSummaryDto(
				qa.getId(),
				endpoint.getId(),
				tagOrDefault(endpoint.getTag()),
				endpoint.getPath(),
				endpoint.getMethod(),
				qa.getDescription(),
				qa.getIsSuccess()
			))
			.toList();
	}

	public QaCaseDetailDto getQaCaseDetail(Long qaId) {
		QaCase qaCase = qaCaseRepository.findById(qaId)
			.orElseThrow(() -> new CustomException(QaErrorCode.QA_NOT_FOUND));

		Endpoint endpoint = qaCase.getEndpoint();

		List<EndpointParameterDto> parameters = swaggerParameterRepository
			.findByEndpointId(endpoint.getId()).stream()
			.map(p -> EndpointParameterDto.fromEntity(p, objectMapper))
			.toList();

		List<EndpointRequestBodyDto> requests = swaggerRequestRepository
			.findByEndpointId(endpoint.getId()).stream()
			.map(r -> EndpointRequestBodyDto.fromEntity(r, objectMapper))
			.toList();

		List<EndpointResponseDto> responses = swaggerResponseRepository
			.findByEndpointId(endpoint.getId()).stream()
			.map(r -> EndpointResponseDto.fromEntity(r, objectMapper))
			.toList();

		List<EndpointSecurityDto> security = swaggerEndpointSecurityRepository
			.findByEndpointId(endpoint.getId()).stream()
			.map(EndpointSecurityDto::fromEntity)
			.toList();

		QaCaseDetailDto.QaData qaData = new QaCaseDetailDto.QaData(
			parseJsonToMap(qaCase.getPathVariables()),
			parseJsonToMap(qaCase.getQueryParams()),
			qaCase.getHeaders(),
			qaCase.getBody()
		);

		QaExecuteResultDto latestExecuteResult = qaExecuteResultRepository
			.findTopByQaCaseIdOrderByExecutedAtDesc(qaId)
			.map(r -> new QaExecuteResultDto(
				r.getId(),
				r.getHttpStatus(),
				r.getIsSuccess(),
				parseJsonToMap(r.getResponseHeaders()),
				parseJsonToNode(r.getResponseBody()),
				r.getExecutedAt(),
				r.getDurationMs()
			))
			.orElse(null);

		return new QaCaseDetailDto(
			qaCase.getId(),
			endpoint.getId(),
			tagOrDefault(endpoint.getTag()),
			endpoint.getPath(),
			endpoint.getMethod(),
			qaCase.getDescription(),
			qaCase.getIsSuccess(),
			parameters, requests, responses, security,
			qaData,
			latestExecuteResult
		);
	}

	@Transactional
	public QaScenarioResponse createQaCases(Long endpointId) {

		// 1. 데이터 수집 (기존 collectData 호출)
		EndpointAggregate spec = collectData(endpointId);
		Endpoint endpoint = spec.endpoint();

		// 2. LlmQaService를 통해 시나리오 생성 요청 (Step 1 & Step 2 포함)
		LlmQaService.QaOutcome outcome = llmQaService.generateScenarios(spec);

		// 3. 결과 확인 및 예외 처리
		// outcome.raw()가 null이면 LLM 호출 자체 실패, outcome.result()가 null이면 파싱 최종 실패
		if (outcome.raw() == null) {
			log.error("QA-GEN: AI 호출 실패. endpointId={}", endpointId);
			throw new CustomException(QaErrorCode.AI_CALL_FAILED);
		}

		if (outcome.result() == null) {
			log.error("QA-GEN: AI 응답 파싱 최종 실패. rawResponse='{}'", outcome.raw());
			throw new CustomException(QaErrorCode.AI_RESPONSE_PARSING_ERROR);
		}

		// 4. 성공 로그 기록
		QaScenarioResponse result = outcome.result();
		log.info("QA-GEN: 시나리오 생성 성공! 개수: {} (endpointId={})",
			result.scenarios().size(), endpointId);

		// 5. DB 저장 로직 실행
		saveScenariosToDb(endpoint, result);

		log.info("QA-GEN: 성공! {}개의 시나리오가 DB에 저장되었습니다. (endpointId={})",
			result.scenarios().size(), endpointId);

		return result;
	}


	@Transactional
	public Long createManualQaCase(Long endpointId, QaScenarioDetail request) {
		// 1. 대상 엔드포인트 존재 확인
		Endpoint endpoint = endpointRepository.findById(endpointId)
			.orElseThrow(() -> new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND));

		// 2. RequestData 및 ExpectedResponse 추출
		var reqData = request.requestData();
		var expRes = request.expectedResponse();

		// 3. 필드 직렬화 (Map -> JSON Node)
		JsonNode bodyNode = objectMapper.valueToTree(reqData.body());

		// 5. 엔티티 생성
		// 기존 QaCase 엔티티에 expectedStatus, expectedBody, codeSnippet 등의 필드가 있다고 가정합니다.
		QaCase qaCase = QaCase.create(
			endpoint,
			request.scenarioName(),
			request.testType(),
			request.description(),
			null, // pathVariables (필요 시 reqData에서 추출)
			null, // queryParams (필요 시 reqData에서 추출)
			reqData.headers(),
			bodyNode,
			expRes.statusCode()
		);

		QaCase saved = qaCaseRepository.save(qaCase);

		log.info("USER-QA-GEN: 유저 커스텀 시나리오 저장 완료 (endpointId={}, scenario={})",
			endpointId, request.scenarioName());

		return saved.getId();
	}


	private String serializeSafe(Object obj) {
		if (obj == null) return "{}";
		try {
			// 이미 String인 경우 불필요한 직렬화 방지
			if (obj instanceof String s) return s;

			return objectMapper.writeValueAsString(obj);
		} catch (Exception e) {
			// 에러를 던지지 않고 경고 로그만 남김
			log.warn("JSON 직렬화 실패: {} - 객체: {}", e.getMessage(), obj.getClass().getSimpleName());
			return "{}"; // 기본값 반환
		}
	}


	/**
	 * AI 응답 결과를 QaCase 엔티티로 변환하여 일괄 저장
	 */
	private void saveScenariosToDb(Endpoint endpoint, QaScenarioResponse result) {
		List<QaCase> qaCases = result.scenarios().stream()
			.map(scenario -> {
				var req = scenario.requestData();
				var expected=scenario.expectedResponse();

				// Map 객체들을 JSON 문자열로 변환 (DB의 TEXT/LONGTEXT 컬럼 대응)
				Map<String,String> headers=req.headers();

				JsonNode bodyNode=objectMapper.valueToTree(req.body());
				String pathVars = null;
				String queryParams = null;

				return QaCase.create(
					endpoint,
					scenario.scenarioName(), // scenarioName 추가
					scenario.testType(),     // testType 추가
					scenario.description(),
					pathVars,
					queryParams,
					headers,
					bodyNode,                // JsonNode 타입
					expected.statusCode());
			})
			.toList();

		qaCaseRepository.saveAll(qaCases);
		log.info("QA_SAVE: {}개의 시나리오가 저장되었습니다. endpointId={}", qaCases.size(), endpoint.getId());
	}


	private EndpointAggregate collectData(Long endpointId) {
		//관련 정보 수집
		var endpoint=endpointRepository.findById(endpointId)
			.orElseThrow(()->new CustomException(SwaggerErrorCode.ENDPOINTS_NOT_FOUND));
		var security=swaggerEndpointSecurityRepository.findByEndpointId(endpointId);
		var parameters=swaggerParameterRepository.findByEndpointId(endpointId);
		var requests=swaggerRequestRepository.findByEndpointId(endpointId);
		var responses=swaggerResponseRepository.findByEndpointId(endpointId);

		return new EndpointAggregate(endpoint,security,parameters,requests,responses, LocalDateTime.now());
	}

	@Transactional
	public QaExecuteResultDto executeQaCase(Long qaId, String proxyAuthorization) {
		QaCase qa = qaCaseRepository.findById(qaId)
			.orElseThrow(() -> new CustomException(QaErrorCode.QA_NOT_FOUND));

		Long endpointId = qa.getEndpoint().getId();
		Long teamId = qa.getEndpoint().getSnapshot().getTeam().getId();

		ApiExecuteRequest request = new ApiExecuteRequest(
			parseStringMap(qa.getPathVariables()),
			parseStringMap(qa.getQueryParams()),
			qa.getHeaders(),
			qa.getBody()
		);

		long startTime = System.currentTimeMillis();
		try {
			ApiExecuteResponse response = apiExecuteService.execute(endpointId, teamId, request, proxyAuthorization);
			long durationMs = System.currentTimeMillis() - startTime;

			boolean isSuccess = response.httpStatus() >= 200 && response.httpStatus() < 300;
			qa.updateIsSuccess(isSuccess);

			String headersJson = serializeToJson(response.responseHeaders());
			String bodyJson = serializeToJson(response.body());

			QaExecuteResult result = QaExecuteResult.create(
				qa, response.httpStatus(), isSuccess, headersJson, bodyJson, durationMs
			);
			qaExecuteResultRepository.save(result);

			return new QaExecuteResultDto(
				result.getId(),
				result.getHttpStatus(),
				result.getIsSuccess(),
				response.responseHeaders(),
				response.body(),
				result.getExecutedAt(),
				result.getDurationMs()
			);
		} catch (CustomException e) {
			long durationMs = System.currentTimeMillis() - startTime;
			qa.updateIsSuccess(false);

			QaExecuteResult result = QaExecuteResult.createFailed(qa, e.getMessage(), durationMs);
			qaExecuteResultRepository.save(result);

			return new QaExecuteResultDto(
				result.getId(),
				result.getHttpStatus(),
				result.getIsSuccess(),
				null,
				result.getResponseBody(),
				result.getExecutedAt(),
				result.getDurationMs()
			);
		}
	}

	public List<QaTeamFailureResponse> getTeamFailures(Long teamId) {
		return swaggerSnapshotRepository.findTopByTeamIdOrderByIdDesc(teamId)
			.map(snapshot -> {
				List<Endpoint> endpoints = endpointRepository.findBySnapshotId(snapshot.getId());
				if (endpoints.isEmpty()) return Collections.<QaTeamFailureResponse>emptyList();

				List<Long> endpointIds = endpoints.stream().map(Endpoint::getId).toList();
				List<QaCase> allCases = qaCaseRepository.findAllByEndpointIdIn(endpointIds);

				return allCases.stream()
					.flatMap(qa -> qaExecuteResultRepository
						.findTopByQaCaseIdOrderByExecutedAtDesc(qa.getId())
						.filter(result -> Boolean.FALSE.equals(result.getIsSuccess()))
						.map(result -> {
							Endpoint ep = qa.getEndpoint();
							return new QaTeamFailureResponse(
								qa.getId(),
								ep.getId(),
								ep.getPath(),
								ep.getMethod() != null ? ep.getMethod().name() : null,
								ep.getTag(),
								qa.getDescription(),
								parseStringMap(qa.getPathVariables()),
								parseStringMap(qa.getQueryParams()),
								qa.getHeaders(),
								qa.getBody(),
								new QaTeamFailureResponse.LatestResult(
									result.getHttpStatus(),
									parseBody(result.getResponseBody()),
									result.getDurationMs(),
									result.getExecutedAt()
								)
							);
						})
						.stream()
					)
					.toList();
			})
			.orElse(Collections.emptyList());
	}

	public List<EndpointQaTagGroupResponse> getEndpointsByTag(Long teamId) {
		return swaggerSnapshotRepository.findTopByTeamIdOrderByIdDesc(teamId)
			.map(snapshot -> {
				List<Endpoint> endpoints = endpointRepository.findBySnapshotId(snapshot.getId());
				if (endpoints.isEmpty()) {
					return Collections.<EndpointQaTagGroupResponse>emptyList();
				}

				List<Long> endpointIds = endpoints.stream().map(Endpoint::getId).toList();
				Map<Long, List<QaCase>> qaCasesByEndpoint = qaCaseRepository.findAllByEndpointIdIn(endpointIds)
					.stream()
					.collect(Collectors.groupingBy(qa -> qa.getEndpoint().getId()));

				return endpoints.stream()
					.collect(Collectors.groupingBy(
						ep -> ep.getTag() != null ? ep.getTag() : "",
						Collectors.mapping(ep -> {
							List<QaCase> cases = qaCasesByEndpoint.getOrDefault(ep.getId(), List.of());
							Double successRate = cases.isEmpty() ? null
								: (double) cases.stream().filter(qa -> Boolean.TRUE.equals(qa.getIsSuccess())).count()
								/ cases.size() * 100;
							return new EndpointQaSummaryResponse(ep.getId(), ep.getMethod(), ep.getPath(), successRate);
						}, Collectors.toList())
					))
					.entrySet().stream()
					.map(e -> new EndpointQaTagGroupResponse(e.getKey(), e.getValue()))
					.toList();
			})
			.orElse(Collections.emptyList());
	}

	private String tagOrDefault(String tag) {
		return tag != null ? tag : "default";
	}

	private Map<String, String> parseJsonToMap(String json) {
		if (json == null || json.isBlank()) return Collections.emptyMap();
		try {
			return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
		} catch (Exception e) {
			return Collections.emptyMap();
		}
	}

	private com.fasterxml.jackson.databind.JsonNode parseJsonToNode(String json) {
		if (json == null || json.isBlank()) return null;
		try {
			return objectMapper.readTree(json);
		} catch (Exception e) {
			return null;
		}
	}

	private Map<String, String> parseStringMap(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(json, new TypeReference<>() {});
		} catch (JsonProcessingException e) {
			throw new CustomException(QaErrorCode.QA_JSON_PROCESSING_ERROR);
		}
	}

	private Object parseBody(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readTree(json);
		} catch (JsonProcessingException e) {
			return json;
		}
	}

	private String serializeToJson(Object obj) {
		if (obj == null) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			throw new CustomException(QaErrorCode.QA_JSON_PROCESSING_ERROR);
		}
	}
}
