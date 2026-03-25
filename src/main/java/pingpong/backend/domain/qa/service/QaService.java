package pingpong.backend.domain.qa.service;

import static java.util.stream.Collectors.toMap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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
import pingpong.backend.domain.qa.QaParamDefault;
import pingpong.backend.domain.qa.QaSyncHistory;
import pingpong.backend.domain.qa.dto.EndpointQaSummaryResponse;
import pingpong.backend.domain.qa.dto.EndpointQaTagGroupResponse;
import pingpong.backend.domain.qa.dto.EndpointRequestBodyDto;
import pingpong.backend.domain.qa.dto.EndpointResponseDto;
import pingpong.backend.domain.qa.dto.EndpointSecurityDto;
import pingpong.backend.domain.qa.dto.QaBulkExecuteResponse;
import pingpong.backend.domain.qa.dto.QaCaseDetailDto;
import pingpong.backend.domain.qa.dto.QaCaseSummaryDto;
import pingpong.backend.domain.qa.dto.QaExecuteResultDto;
import pingpong.backend.domain.qa.dto.QaPathVariableRequest;
import pingpong.backend.domain.qa.dto.QaPathVariableResponse;
import pingpong.backend.domain.qa.dto.QaReRunRequest;
import pingpong.backend.domain.qa.dto.QaScenarioDetail;
import pingpong.backend.domain.qa.dto.QaScenarioRequest;
import pingpong.backend.domain.qa.dto.QaScenarioResponse;
import pingpong.backend.domain.qa.dto.QaTeamFailureResponse;
import pingpong.backend.domain.qa.enums.SourceType;
import pingpong.backend.domain.qa.enums.TestType;
import pingpong.backend.domain.qa.repository.QaSyncHistoryRepository;
import pingpong.backend.domain.swaggerdiff.dto.EndpointParameterDto;
import pingpong.backend.domain.qa.repository.QaCaseRepository;
import pingpong.backend.domain.qa.repository.QaExecuteResultRepository;
import pingpong.backend.domain.qa.repository.QaParamDefaultRepository;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.SwaggerParameter;
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
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.TeamErrorCode;
import pingpong.backend.domain.team.repository.TeamRepository;
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
	private final QaParamDefaultRepository qaParamDefaultRepository;
	private final TeamRepository teamRepository;


	public QaService(QaCaseRepository qaCaseRepository,
		QaExecuteResultRepository qaExecuteResultRepository, ApiExecuteService apiExecuteService,
		ObjectMapper objectMapper, SwaggerSnapshotRepository swaggerSnapshotRepository,
		EndpointRepository endpointRepository, SwaggerEndpointSecurityRepository swaggerEndpointSecurityRepository,
		SwaggerParameterRepository swaggerParameterRepository, SwaggerRequestRepository swaggerRequestRepository,
		SwaggerResponseRepository swaggerResponseRepository, LlmQaService llmQaService,
		QaSyncHistoryRepository qaSyncHistoryRepository, QaParamDefaultRepository qaParamDefaultRepository,
		TeamRepository teamRepository) {
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
		this.qaParamDefaultRepository = qaParamDefaultRepository;
		this.teamRepository = teamRepository;
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
				qa.getScenarioName(),
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
			qaCase.getBody(),
			qaCase.getExpectedStatusCode()
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
				r.getDurationMs(),
				qaCase.getExpectedStatusCode()
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

		// 1-1. 팀의 path variable 기본값 로딩
		Long teamId = endpoint.getSnapshot().getTeam().getId();
		Map<String, String> paramDefaults = loadParamDefaults(teamId);

		// 2. LlmQaService를 통해 시나리오 생성 요청 (Step 1 & Step 2 포함)
		LlmQaService.QaOutcome outcome = llmQaService.generateScenarios(spec, paramDefaults);

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

		// 5. DB 저장 로직 실행 (POSITIVE 케이스 후처리 포함)
		saveScenariosToDb(endpoint, result, paramDefaults);

		log.info("QA-GEN: 성공! {}개의 시나리오가 DB에 저장되었습니다. (endpointId={})",
			result.scenarios().size(), endpointId);

		return result;
	}


	@Transactional
	public Long createManualQaCase(Long endpointId, QaScenarioRequest request) {
		// 1. 대상 엔드포인트 존재 확인
		Endpoint endpoint = endpointRepository.findById(endpointId)
			.orElseThrow(() -> new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND));

		// 2. RequestData 및 ExpectedResponse 추출
		var reqData = request.qaData();
		// 3. 필드 직렬화 (Map -> JSON Node)
		JsonNode bodyNode = objectMapper.valueToTree(reqData.body());

		// 5. 엔티티 생성
		// 기존 QaCase 엔티티에 expectedStatus, expectedBody, codeSnippet 등의 필드가 있다고 가정합니다.
		QaCase qaCase = QaCase.create(
			endpoint,
			request.scenarioName(),
			request.testType(),
			request.description(),
			serializeSafe(reqData.pathVariables()), // pathVariables (필요 시 reqData에서 추출)
			serializeSafe(reqData.queryParams()), // queryParams (필요 시 reqData에서 추출)
			reqData.headers(),
			bodyNode,
			SourceType.MANUAL,
			reqData.expectedStatusCode()
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
	 * POSITIVE 케이스의 path variable은 사용자 기본값으로 후처리
	 */
	private void saveScenariosToDb(Endpoint endpoint, QaScenarioResponse result,
		Map<String, String> paramDefaults) {

		// path 타입 파라미터의 스키마 타입 매핑 (후처리 fallback용)
		Map<String, String> pathParamSchemaTypes = loadPathParamSchemaTypes(endpoint.getId());

		List<QaCase> qaCases = result.scenarios().stream()
			.map(scenario -> {
				var req = scenario.requestData();
				var expected = scenario.expectedResponse();

				Map<String, String> headers = req.headers();
				JsonNode bodyNode = objectMapper.valueToTree(req.body());

				// path variable 후처리: NEGATIVE 404 테스트만 제외하고 모든 케이스에 적용
				Map<String, String> pathVars = req.pathVariables() != null
					? new HashMap<>(req.pathVariables()) : new HashMap<>();
				boolean isNotFoundTest = scenario.testType() == TestType.NEGATIVE && expected.statusCode() == 404;
				if (!isNotFoundTest) {
					applyPathVariableDefaults(pathVars, paramDefaults, pathParamSchemaTypes);
				}

				return QaCase.create(
					endpoint,
					scenario.scenarioName(),
					scenario.testType(),
					scenario.description(),
					serializeSafe(pathVars),
					serializeSafe(req.queryParams()),
					headers,
					bodyNode,
					SourceType.AI_GENERATED,
					expected.statusCode());
			})
			.toList();

		qaCaseRepository.saveAll(qaCases);
		log.info("QA_SAVE: {}개의 시나리오가 저장되었습니다. endpointId={}", qaCases.size(), endpoint.getId());
	}

	/**
	 * POSITIVE 케이스의 path variable에 사용자 기본값을 적용하고,
	 * 미제공 시 integer ID 파라미터는 "1"로 대체
	 */
	private void applyPathVariableDefaults(Map<String, String> pathVars,
		Map<String, String> paramDefaults, Map<String, String> pathParamSchemaTypes) {
		for (String paramName : pathVars.keySet()) {
			// 1순위: 사용자 제공 기본값
			if (paramDefaults.containsKey(paramName)) {
				pathVars.put(paramName, paramDefaults.get(paramName));
			}
			// 2순위: integer 타입 ID 파라미터는 "1"로 fallback
			else if (isIntegerIdParam(paramName, pathParamSchemaTypes.get(paramName))) {
				pathVars.put(paramName, "1");
			}
		}
	}

	private boolean isIntegerIdParam(String paramName, String schemaType) {
		if (!paramName.toLowerCase().endsWith("id")) {
			return false;
		}
		// schema type이 integer이거나, schema 정보가 없는 경우(null/"") Id로 끝나는 파라미터는 ID로 간주
		return schemaType == null || schemaType.isBlank() || "integer".equals(schemaType);
	}

	private Map<String, String> loadParamDefaults(Long teamId) {
		return qaParamDefaultRepository.findByTeamId(teamId).stream()
			.filter(d -> d.getParamValue() != null && !d.getParamValue().isBlank())
			.collect(toMap(QaParamDefault::getParamName, QaParamDefault::getParamValue));
	}

	private Map<String, String> loadPathParamSchemaTypes(Long endpointId) {
		return swaggerParameterRepository.findByEndpointId(endpointId).stream()
			.filter(p -> "path".equals(p.getInType()))
			.collect(toMap(SwaggerParameter::getName, p -> extractSchemaType(p.getSchemaJson()), (a, b) -> a));
	}

	private String extractSchemaType(String schemaJson) {
		if (schemaJson == null || schemaJson.isBlank()) return "";
		try {
			JsonNode schema = objectMapper.readTree(schemaJson);
			return schema.has("type") ? schema.get("type").asText() : "";
		} catch (Exception e) {
			return "";
		}
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
	public QaExecuteResultDto reRunQaCase(Long qaId, QaReRunRequest reRunData, String proxyAuthorization) {
		// 1. 기존 QA 케이스 정보 조회 (엔드포인트 정보 참조용)
		QaCase qa = qaCaseRepository.findById(qaId)
			.orElseThrow(() -> new CustomException(QaErrorCode.QA_NOT_FOUND));

		// 2. 화면에서 넘어온 수정 데이터로 DB 업데이트 (기존 엔티티 필드 갱신)
		// pathVariables와 queryParams가 String(JSON)이라면 여기서 직렬화 처리
		qa.updateTestData(
			serializeToJson(reRunData.pathVariables()),
			serializeToJson(reRunData.queryParams()),
			reRunData.headers(),
			reRunData.body(),
			SourceType.AI_ASSISTED
		);

		// 3. 기존의 실행 로직을 그대로 호출 (재사용!)
		// 이미 엔티티가 업데이트되었으므로 executeQaCase는 수정된 데이터를 읽어서 실행합니다.
		return executeQaCase(qaId, proxyAuthorization);
	}

	public List<QaExecuteResultDto> getQaExecuteResults(Long qaId) {
		// 1. 해당 QA 케이스가 존재하는지 확인 (기대 상태 코드를 가져오기 위함)
		QaCase qaCase = qaCaseRepository.findById(qaId)
			.orElseThrow(() -> new CustomException(QaErrorCode.QA_NOT_FOUND));

		// 2. 실행 내역 조회
		List<QaExecuteResult> results = qaExecuteResultRepository.findByQaCaseIdOrderByExecutedAtDesc(qaId);

		// 3. 엔티티 리스트를 DTO 리스트로 변환
		return results.stream()
			.map(result -> QaExecuteResultDto.fromEntity(
				result,
				parseJsonToMap(result.getResponseHeaders()), // JSON 문자열 -> Map
				parseJsonToNode(result.getResponseBody()),   // JSON 문자열 -> JsonNode
				qaCase.getExpectedStatusCode()              // QA 케이스의 기대 코드
			))
			.toList();
	}


	@Retryable(retryFor = CannotAcquireLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
	@Transactional
	public QaExecuteResultDto executeQaCase(Long qaId, String proxyAuthorization) {
		QaCase qa = qaCaseRepository.findById(qaId)
			.orElseThrow(() -> new CustomException(QaErrorCode.QA_NOT_FOUND));

		Long endpointId = qa.getEndpoint().getId();
		Long teamId = qa.getEndpoint().getSnapshot().getTeam().getId();

		// path variable 후처리: NEGATIVE 404 테스트만 제외하고 모든 케이스에 적용
		Map<String, String> pathVars = parseStringMap(qa.getPathVariables());
		boolean isNotFoundTest = qa.getTestType() == TestType.NEGATIVE && qa.getExpectedStatusCode() == 404;
		if (pathVars != null && !isNotFoundTest) {
			pathVars = new HashMap<>(pathVars);
			Map<String, String> paramDefaults = loadParamDefaults(teamId);
			Map<String, String> schemaTypes = loadPathParamSchemaTypes(endpointId);
			applyPathVariableDefaults(pathVars, paramDefaults, schemaTypes);
		}

		ApiExecuteRequest request = new ApiExecuteRequest(
			pathVars,
			parseStringMap(qa.getQueryParams()),
			qa.getHeaders(),
			qa.getBody()
		);

		long startTime = System.currentTimeMillis();
		try {
			ApiExecuteResponse response = apiExecuteService.execute(endpointId, teamId, request, proxyAuthorization);
			long durationMs = System.currentTimeMillis() - startTime;

			// 성공 여부 판단 로직 변경
			// 단순히 200번대가 아니라, '기대한 상태 코드'와 일치하는지 확인
			int actualStatus = response.httpStatus();
			int expectedStatus = qa.getExpectedStatusCode();

			boolean isStatusMatch = (actualStatus == expectedStatus);

			// 추가: 만약 바디 필드까지 엄격하게 검증하고 싶다면 여기에 추가 로직을 넣습니다.
			// boolean isBodyMatch = checkBodyFields(response.body(), qa.getExpectedBodyFields());

			boolean finalSuccess = isStatusMatch;
			qa.updateIsSuccess(finalSuccess);

			String headersJson = serializeToJson(response.responseHeaders());
			String bodyJson = serializeToJson(response.body());

			QaExecuteResult result = QaExecuteResult.create(
				qa, response.httpStatus(), finalSuccess, headersJson, bodyJson, durationMs
			);
			qaExecuteResultRepository.save(result);

			return new QaExecuteResultDto(
				result.getId(),
				result.getHttpStatus(),
				result.getIsSuccess(),
				null,
				response.body(),
				result.getExecutedAt(),
				result.getDurationMs(),
				qa.getExpectedStatusCode()
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
				result.getDurationMs(),
				qa.getExpectedStatusCode()
			);
		}
	}

	/**
	 * qa를 한 번에 여러개 실행
	 * @param qaIds
	 * @param proxyAuthorization
	 * @return
	 */
	public QaBulkExecuteResponse executeBulkQaCases(List<Long> qaIds, String proxyAuthorization) {
		List<QaExecuteResultDto> results = new ArrayList<>();
		int successCount = 0;

		for (Long qaId : qaIds) {
			try {
				// 기존 단일 실행 로직 재활용
				QaExecuteResultDto result = executeQaCase(qaId, proxyAuthorization);
				results.add(result);

				if (result.isSuccess()) {
					successCount++;
				}
			} catch (Exception e) {
				// 특정 ID 실행 중 에러가 나도 로그만 남기고 다음 ID로 넘어감
				log.error("Bulk 실행 중 오류 발생 - qaId: {}, error: {}", qaId, e.getMessage());
				// 실패한 결과도 리스트에 추가 (에러 메시지 포함)
				results.add(createFailedResultDto(qaId, e.getMessage()));
			}
		}

		return new QaBulkExecuteResponse(
			qaIds.size(),
			successCount,
			qaIds.size() - successCount,
			results
		);
	}

	// 실패 시 응답을 위한 간단한 헬퍼 메서드
	private QaExecuteResultDto createFailedResultDto(Long qaId, String message) {
		return new QaExecuteResultDto(null, 500, false, null, message, LocalDateTime.now(), 0L, 0);
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

	/**
	 * 스웨거 sync 시 호출: 최신 스냅샷의 path 파라미터 기준으로 QaParamDefault를 동기화
	 */
	@Transactional
	public void syncPathVariableDefaults(Long teamId) {
		var snapshotOpt = swaggerSnapshotRepository.findTopByTeamIdOrderByIdDesc(teamId);
		if (snapshotOpt.isEmpty()) return;

		List<Endpoint> endpoints = endpointRepository.findBySnapshotId(snapshotOpt.get().getId());
		if (endpoints.isEmpty()) return;

		List<Long> endpointIds = endpoints.stream().map(Endpoint::getId).toList();
		List<SwaggerParameter> pathParams = swaggerParameterRepository
			.findByEndpointIdInAndInType(endpointIds, "path");

		// 스웨거 기준 고유 path variable 이름 수집
		Map<String, String> currentParams = new LinkedHashMap<>();
		for (SwaggerParameter param : pathParams) {
			currentParams.putIfAbsent(param.getName(), extractSchemaType(param.getSchemaJson()));
		}

		// DB 기존 레코드와 비교하여 upsert
		List<QaParamDefault> existingDefaults = qaParamDefaultRepository.findByTeamId(teamId);
		Map<String, QaParamDefault> existingMap = existingDefaults.stream()
			.collect(toMap(QaParamDefault::getParamName, d -> d));

		// 사라진 param 삭제
		List<QaParamDefault> toDelete = existingDefaults.stream()
			.filter(d -> !currentParams.containsKey(d.getParamName()))
			.toList();
		if (!toDelete.isEmpty()) {
			qaParamDefaultRepository.deleteAll(toDelete);
		}

		// 새로 등장한 param 추가 (빈 값)
		if (!currentParams.isEmpty()) {
			Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new CustomException(TeamErrorCode.TEAM_NOT_FOUND));
			List<QaParamDefault> toAdd = currentParams.keySet().stream()
				.filter(name -> !existingMap.containsKey(name))
				.map(name -> QaParamDefault.create(team, name, ""))
				.toList();
			if (!toAdd.isEmpty()) {
				qaParamDefaultRepository.saveAll(toAdd);
			}
		}

		log.info("QA_PARAM_SYNC: teamId={} path variable 기본값 동기화 완료", teamId);
	}

	/**
	 * 팀의 path variable 기본값 목록 조회 (순수 조회 전용)
	 */
	public List<QaPathVariableResponse> getPathVariableList(Long teamId) {
		var snapshotOpt = swaggerSnapshotRepository.findTopByTeamIdOrderByIdDesc(teamId);
		if (snapshotOpt.isEmpty()) return Collections.emptyList();

		List<Endpoint> endpoints = endpointRepository.findBySnapshotId(snapshotOpt.get().getId());
		if (endpoints.isEmpty()) return Collections.emptyList();

		List<Long> endpointIds = endpoints.stream().map(Endpoint::getId).toList();
		List<SwaggerParameter> pathParams = swaggerParameterRepository
			.findByEndpointIdInAndInType(endpointIds, "path");

		// 스웨거 기준 스키마 타입 매핑 (응답용)
		Map<String, String> schemaTypeMap = new LinkedHashMap<>();
		for (SwaggerParameter param : pathParams) {
			schemaTypeMap.putIfAbsent(param.getName(), extractSchemaType(param.getSchemaJson()));
		}

		List<QaParamDefault> defaults = qaParamDefaultRepository.findByTeamId(teamId);
		return defaults.stream()
			.map(d -> new QaPathVariableResponse(
				d.getId(),
				d.getParamName(),
				schemaTypeMap.getOrDefault(d.getParamName(), ""),
				d.getParamValue()
			))
			.toList();
	}

	@Transactional
	public void updatePathVariableDefaults(List<QaPathVariableRequest.ParamUpdate> params) {
		for (QaPathVariableRequest.ParamUpdate param : params) {
			QaParamDefault entity = qaParamDefaultRepository.findById(param.id())
				.orElseThrow(() -> new CustomException(QaErrorCode.QA_NOT_FOUND));
			entity.updateValue(param.value());
		}
		log.info("QA_PARAM: {}개의 path variable 기본값 수정 완료", params.size());
	}

	private String tagOrDefault(String tag) {
		return tag != null ? tag : "default";
	}

	private Map<String, String> parseJsonToMap(String json) {
		if (json == null || json.isBlank()) return Collections.emptyMap();
		try {
			return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
		} catch (Exception e) {
			log.warn("JSON → Map 파싱 실패: {}", e.getMessage());
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
