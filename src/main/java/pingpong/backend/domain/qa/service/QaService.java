package pingpong.backend.domain.qa.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.qa.QaCase;
import pingpong.backend.domain.qa.QaErrorCode;
import pingpong.backend.domain.qa.QaExecuteResult;
import pingpong.backend.domain.qa.dto.EndpointQaSummaryResponse;
import pingpong.backend.domain.qa.dto.EndpointQaTagGroupResponse;
import pingpong.backend.domain.qa.dto.QaCaseResponse;
import pingpong.backend.domain.qa.dto.QaExecuteResultResponse;
import pingpong.backend.domain.qa.dto.QaTeamFailureResponse;
import pingpong.backend.domain.qa.repository.QaCaseRepository;
import pingpong.backend.domain.qa.repository.QaExecuteResultRepository;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.dto.request.ApiExecuteRequest;
import pingpong.backend.domain.swagger.dto.response.ApiExecuteResponse;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.swagger.repository.SwaggerSnapshotRepository;
import pingpong.backend.domain.swagger.service.ApiExecuteService;
import pingpong.backend.global.exception.CustomException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QaService {

	private final QaCaseRepository qaCaseRepository;
	private final QaExecuteResultRepository qaExecuteResultRepository;
	private final ApiExecuteService apiExecuteService;
	private final ObjectMapper objectMapper;
	private final SwaggerSnapshotRepository swaggerSnapshotRepository;
	private final EndpointRepository endpointRepository;

	public List<QaCaseResponse> getQaCasesByEndpointId(Long endpointId) {
		return qaCaseRepository.findAllByEndpointId(endpointId).stream()
			.map(qa -> new QaCaseResponse(
				qa.getId(),
				qa.getEndpoint().getId(),
				qa.getIsSuccess(),
				qa.getDescription(),
				parseStringMap(qa.getPathVariables()),
				parseStringMap(qa.getQueryParams()),
				parseStringMap(qa.getHeaders()),
				parseBody(qa.getBody()),
				qa.getCreatedAt()
			))
			.toList();
	}

	@Transactional
	public QaExecuteResultResponse executeQaCase(Long qaId, String proxyAuthorization) {
		QaCase qa = qaCaseRepository.findById(qaId)
			.orElseThrow(() -> new CustomException(QaErrorCode.QA_NOT_FOUND));

		Long endpointId = qa.getEndpoint().getId();
		Long teamId = qa.getEndpoint().getSnapshot().getTeam().getId();

		ApiExecuteRequest request = new ApiExecuteRequest(
			parseStringMap(qa.getPathVariables()),
			parseStringMap(qa.getQueryParams()),
			parseStringMap(qa.getHeaders()),
			parseBody(qa.getBody())
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

			return new QaExecuteResultResponse(
				result.getId(),
				qa.getId(),
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

			return new QaExecuteResultResponse(
				result.getId(),
				qa.getId(),
				result.getHttpStatus(),
				result.getIsSuccess(),
				null,
				result.getResponseBody(),
				result.getExecutedAt(),
				result.getDurationMs()
			);
		}
	}

	public List<QaExecuteResultResponse> getExecuteResults(Long qaCaseId) {
		if (!qaCaseRepository.existsById(qaCaseId)) {
			throw new CustomException(QaErrorCode.QA_NOT_FOUND);
		}
		return qaExecuteResultRepository.findAllByQaCaseIdOrderByExecutedAtDesc(qaCaseId).stream()
			.map(r -> new QaExecuteResultResponse(
				r.getId(),
				r.getQaCase().getId(),
				r.getHttpStatus(),
				r.getIsSuccess(),
				parseStringMap(r.getResponseHeaders()),
				parseBody(r.getResponseBody()),
				r.getExecutedAt(),
				r.getDurationMs()
			))
			.toList();
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
								parseStringMap(qa.getHeaders()),
								parseBody(qa.getBody()),
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
