package pingpong.backend.domain.qaeval.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.qa.QaCase;
import pingpong.backend.domain.qa.QaSyncHistory;
import pingpong.backend.domain.qa.enums.TestType;
import pingpong.backend.domain.qa.repository.QaCaseRepository;
import pingpong.backend.domain.qa.repository.QaSyncHistoryRepository;
import pingpong.backend.domain.qaeval.dto.ExecutionSummaryResponse;
import pingpong.backend.domain.qaeval.dto.FailedCaseItem;
import pingpong.backend.domain.qaeval.dto.QaEvalSummaryResponse;
import pingpong.backend.domain.qaeval.dto.SchemaComparisonResult;
import pingpong.backend.domain.qaeval.dto.SyncHistoryItem;
import pingpong.backend.domain.qaeval.dto.TeamItem;
import pingpong.backend.domain.qaeval.dto.TestTypeDistribution;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerParameter;
import pingpong.backend.domain.swagger.SwaggerRequest;
import pingpong.backend.domain.swagger.SwaggerSnapshot;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.swagger.repository.SwaggerParameterRepository;
import pingpong.backend.domain.swagger.repository.SwaggerRequestRepository;
import pingpong.backend.domain.swagger.repository.SwaggerSnapshotRepository;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.repository.TeamRepository;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QaEvalService {

	private final QaCaseRepository qaCaseRepository;
	private final QaSyncHistoryRepository qaSyncHistoryRepository;
	private final SwaggerSnapshotRepository swaggerSnapshotRepository;
	private final EndpointRepository endpointRepository;
	private final SwaggerRequestRepository swaggerRequestRepository;
	private final SwaggerParameterRepository swaggerParameterRepository;
	private final TeamRepository teamRepository;
	private final SchemaComparisonService schemaComparisonService;

	public QaEvalSummaryResponse buildSummary(Long teamId) {
		// 1. 최신 스냅샷 엔드포인트 (스키마 비교 기준)
		List<Endpoint> latestEndpoints = resolveEndpoints(teamId);

		if (latestEndpoints.isEmpty()) {
			return emptyResponse(teamId);
		}

		Set<Long> latestEndpointIds = latestEndpoints.stream()
			.map(Endpoint::getId)
			.collect(Collectors.toSet());

		// 2. 최신 path+method 기준으로 팀 전체 스냅샷의 endpoint ID 수집
		//    (QA 케이스가 이전 스냅샷의 endpoint에 연결되어 있을 수 있음)
		Map<String, Endpoint> pathMethodToLatest = new HashMap<>();
		for (Endpoint ep : latestEndpoints) {
			pathMethodToLatest.put(ep.getPath() + "::" + ep.getMethod(), ep);
		}

		List<Endpoint> allTeamEndpoints = resolveAllTeamEndpoints(teamId);
		Map<Long, Long> oldToLatestId = new HashMap<>();
		for (Endpoint ep : allTeamEndpoints) {
			String key = ep.getPath() + "::" + ep.getMethod();
			Endpoint latest = pathMethodToLatest.get(key);
			if (latest != null) {
				oldToLatestId.put(ep.getId(), latest.getId());
			}
		}

		Set<Long> allMatchingIds = oldToLatestId.keySet();

		// 3. QA 케이스 조회 (모든 스냅샷의 매칭 endpoint ID로)
		List<QaCase> allCases = qaCaseRepository.findAllByEndpointIdIn(allMatchingIds);

		if (allCases.isEmpty()) {
			return emptyResponse(teamId);
		}

		// 4. 최신 스냅샷 기준 스키마 배치 조회
		List<SwaggerRequest> allRequests = swaggerRequestRepository.findByEndpointIdIn(latestEndpointIds);
		List<SwaggerParameter> allQueryParams = swaggerParameterRepository
			.findByEndpointIdInAndInType(latestEndpointIds, "query");

		// 5. Map 구성 — QA 케이스의 옛 endpoint ID도 최신 스키마/endpoint로 매핑
		Map<Long, Endpoint> endpointMap = latestEndpoints.stream()
			.collect(Collectors.toMap(Endpoint::getId, e -> e));
		for (Map.Entry<Long, Long> entry : oldToLatestId.entrySet()) {
			endpointMap.putIfAbsent(entry.getKey(), endpointMap.get(entry.getValue()));
		}

		Map<Long, List<SwaggerRequest>> requestMap = allRequests.stream()
			.collect(Collectors.groupingBy(r -> r.getEndpoint().getId()));
		for (Map.Entry<Long, Long> entry : oldToLatestId.entrySet()) {
			requestMap.putIfAbsent(entry.getKey(), requestMap.getOrDefault(entry.getValue(), Collections.emptyList()));
		}

		Map<Long, List<SwaggerParameter>> paramMap = allQueryParams.stream()
			.collect(Collectors.groupingBy(p -> p.getEndpoint().getId()));
		for (Map.Entry<Long, Long> entry : oldToLatestId.entrySet()) {
			paramMap.putIfAbsent(entry.getKey(), paramMap.getOrDefault(entry.getValue(), Collections.emptyList()));
		}

		// 6. 스키마 비교
		SchemaComparisonResult bodyComparison = schemaComparisonService.compareBody(
			allCases, requestMap, endpointMap);

		SchemaComparisonResult queryParamComparison = schemaComparisonService.compareQueryParams(
			allCases, paramMap, endpointMap);

		// 7. 실행 성공률 (QaCase.isSuccess 기준)
		ExecutionSummaryResponse executionSummary = buildExecutionSummary(allCases, endpointMap);

		// 8. TestType 분포
		TestTypeDistribution testTypeDist = buildTestTypeDistribution(allCases);

		// 9. SyncHistory 조회
		List<SyncHistoryItem> syncHistories = buildSyncHistories(teamId);

		return new QaEvalSummaryResponse(
			bodyComparison,
			queryParamComparison,
			executionSummary,
			syncHistories,
			testTypeDist
		);
	}

	public List<TeamItem> getAllTeams() {
		return teamRepository.findAll().stream()
			.map(t -> new TeamItem(t.getId(), t.getName()))
			.toList();
	}

	// ── Private helpers ──

	private List<Endpoint> resolveAllTeamEndpoints(Long teamId) {
		if (teamId != null) {
			return endpointRepository.findAllBySnapshotTeamId(teamId);
		}
		List<Long> teamIds = teamRepository.findAll().stream()
			.map(Team::getId)
			.toList();
		if (teamIds.isEmpty()) {
			return Collections.emptyList();
		}
		return endpointRepository.findAllBySnapshotTeamIdIn(teamIds);
	}

	private List<Endpoint> resolveEndpoints(Long teamId) {
		if (teamId != null) {
			return swaggerSnapshotRepository.findTopByTeamIdOrderByIdDesc(teamId)
				.map(snapshot -> endpointRepository.findBySnapshotId(snapshot.getId()))
				.orElse(Collections.emptyList());
		}

		// 전체 팀: 각 팀의 최신 스냅샷에서 엔드포인트 수집
		List<Team> teams = teamRepository.findAll();
		List<Endpoint> allEndpoints = new ArrayList<>();
		for (Team team : teams) {
			swaggerSnapshotRepository.findTopByTeamIdOrderByIdDesc(team.getId())
				.ifPresent(snapshot ->
					allEndpoints.addAll(endpointRepository.findBySnapshotId(snapshot.getId()))
				);
		}
		return allEndpoints;
	}

	private ExecutionSummaryResponse buildExecutionSummary(
		List<QaCase> cases, Map<Long, Endpoint> endpointMap
	) {
		int successCount = 0;
		int failCount = 0;
		List<FailedCaseItem> failedCases = new ArrayList<>();

		for (QaCase qa : cases) {
			if (qa.getIsSuccess() == null) {
				// 아직 실행되지 않은 케이스는 통계에서 제외
				continue;
			}
			if (Boolean.TRUE.equals(qa.getIsSuccess())) {
				successCount++;
			} else {
				failCount++;
				Endpoint ep = endpointMap.get(qa.getEndpoint().getId());
				if (ep != null) {
					failedCases.add(new FailedCaseItem(
						qa.getId(),
						qa.getScenarioName(),
						qa.getTestType() != null ? qa.getTestType().name() : null,
						ep.getId(),
						ep.getPath(),
						ep.getMethod() != null ? ep.getMethod().name() : null
					));
				}
			}
		}

		int totalExecuted = successCount + failCount;
		double successRate = totalExecuted > 0 ? (double) successCount / totalExecuted * 100 : 0.0;

		return new ExecutionSummaryResponse(totalExecuted, successCount, failCount, successRate, failedCases);
	}

	private TestTypeDistribution buildTestTypeDistribution(List<QaCase> cases) {
		int positiveCount = 0;
		int negativeCount = 0;
		for (QaCase qa : cases) {
			if (qa.getTestType() == TestType.POSITIVE) positiveCount++;
			else negativeCount++;
		}
		int total = positiveCount + negativeCount;
		return new TestTypeDistribution(
			positiveCount,
			negativeCount,
			total > 0 ? (double) positiveCount / total * 100 : 0.0,
			total > 0 ? (double) negativeCount / total * 100 : 0.0
		);
	}

	private List<SyncHistoryItem> buildSyncHistories(Long teamId) {
		List<QaSyncHistory> histories;
		if (teamId != null) {
			histories = qaSyncHistoryRepository.findTopByTeamIdOrderByIdDesc(teamId)
				.map(List::of)
				.orElse(Collections.emptyList());
		} else {
			histories = qaSyncHistoryRepository.findLatestPerTeam();
		}

		return histories.stream()
			.map(h -> {
				Team team = h.getTeam();
				return new SyncHistoryItem(
					team != null ? team.getId() : null,
					team != null ? team.getName() : null,
					h.getStatus().name(),
					h.getTotalCount(),
					h.getSuccessCount(),
					h.getFailCount(),
					h.getErrorMessage(),
					h.getStartedAt(),
					h.getCompletedAt()
				);
			})
			.toList();
	}

	private QaEvalSummaryResponse emptyResponse(Long teamId) {
		SchemaComparisonResult emptyBody = new SchemaComparisonResult(
			"body", 0, 0, 0, 0, 0.0, 0, 0, 0, 0.0, Collections.emptyList());
		SchemaComparisonResult emptyQuery = new SchemaComparisonResult(
			"queryParams", 0, 0, 0, 0, 0.0, 0, 0, 0, 0.0, Collections.emptyList());
		ExecutionSummaryResponse emptyExec = new ExecutionSummaryResponse(
			0, 0, 0, 0.0, Collections.emptyList());
		TestTypeDistribution emptyDist = new TestTypeDistribution(0, 0, 0.0, 0.0);
		List<SyncHistoryItem> syncHistories = buildSyncHistories(teamId);

		return new QaEvalSummaryResponse(emptyBody, emptyQuery, emptyExec, syncHistories, emptyDist);
	}
}
