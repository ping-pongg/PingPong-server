package pingpong.backend.domain.swagger.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.flow.FlowErrorCode;
import pingpong.backend.domain.flow.FlowImage;
import pingpong.backend.domain.flow.RequestEndpoint;
import pingpong.backend.domain.flow.repository.FlowImageRepository;
import pingpong.backend.domain.flow.repository.RequestEndpointRepository;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.notion.service.NotionFacade;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.SwaggerSnapshot;
import pingpong.backend.domain.swagger.dto.response.EndpointResponse;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.swagger.repository.SwaggerSnapshotRepository;
import pingpong.backend.domain.task.repository.FlowTaskRepository;
import pingpong.backend.domain.task.repository.TaskRepository;
import pingpong.backend.global.exception.CustomException;

@Service
@Slf4j
@RequiredArgsConstructor
public class EndpointService {

	private final RequestEndpointRepository requestEndpointRepository;
	private final EndpointRepository endpointRepository;
	private final SwaggerSnapshotRepository swaggerSnapshotRepository;
	private final FlowImageRepository flowImageRepository;
	private final FlowTaskRepository flowTaskRepository;
	private final TaskRepository taskRepository;
	private final NotionFacade notionFacade;

	/**
	 * 해당 프로젝트에 속한 모든 엔드포인트 조회
	 */
	@Transactional(readOnly = true)
	public List<EndpointResponse> getEndpointList(Long teamId) {
		Optional<SwaggerSnapshot> latest = swaggerSnapshotRepository.findTopByTeamIdOrderByIdDesc(teamId);
		if (latest.isEmpty()) {
			return List.of();
		}
		Long snapshotId = latest.get().getId();

		List<Endpoint> endpoints = endpointRepository.findBySnapshotId(snapshotId);
		return endpoints.stream()
			.map(EndpointResponse::toDto)
			.collect(Collectors.toList());
	}

	/**
	 * 해당 API를 연동 완료 — 해당 이미지에서 이 endpoint를 참조하는 모든 request_endpoint를 linked 처리
	 */
	@Transactional
	public void completeEndpoint(Long flowImageId, Long endpointId, Member member) {
		FlowImage image = flowImageRepository.findById(flowImageId)
			.orElseThrow(() -> new CustomException(FlowErrorCode.FLOW_IMAGE_NOT_FOUND));

		Endpoint endpoint = endpointRepository.findById(endpointId)
			.orElseThrow(() -> new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND));

		List<RequestEndpoint> links =
			requestEndpointRepository.findByImageIdAndEndpointId(flowImageId, endpointId);

		if (links.isEmpty()) {
			throw new CustomException(SwaggerErrorCode.ENDPOINT_NOT_ASSIGNED);
		}

		links.forEach(RequestEndpoint::markLinked);

		syncNotionEndpointStatus(endpoint, image.getFlow().getId());
	}

	/**
	 * 변경된 엔드포인트에 대해 isLinked=false로 변경
	 */
	@Transactional
	public void unlinkChangedEndpoints(List<Endpoint> changedEndpoints) {
		List<Long> endpointIds = changedEndpoints.stream()
			.map(Endpoint::getId)
			.toList();

		if (endpointIds.isEmpty()) {
			return;
		}
		requestEndpointRepository.unlinkChangedEndpoints(endpointIds);
	}

	/**
	 * endpoint의 현재 RequestEndpoint 상태를 기반으로 Notion child DB row의 Status를 동기화한다.
	 * Notion 호출 실패는 메인 트랜잭션에 영향을 주지 않는다 (NotionFacade 내부에서 처리).
	 */
	private void syncNotionEndpointStatus(Endpoint endpoint, Long flowId) {
		List<RequestEndpoint> allLinks = requestEndpointRepository.findAllByEndpointId(endpoint.getId());
		String newStatus;
		if (allLinks.isEmpty()) {
			newStatus = "Backend";
		} else if (allLinks.stream().allMatch(re -> Boolean.TRUE.equals(re.getIsLinked()))) {
			newStatus = "Complete";
		} else {
			newStatus = "Frontend";
		}

		String apiListValue = endpoint.getMethod().name() + " " + endpoint.getPath();

		flowTaskRepository.findAllByFlowId(flowId).forEach(flowTask ->
			taskRepository.findById(flowTask.getTaskId())
				.filter(task -> task.getChildDatabaseId() != null)
				.ifPresent(task -> notionFacade.updateChildDatabaseEndpointStatus(
					task.getTeamId(), task.getChildDatabaseId(), apiListValue, newStatus))
		);
	}
}
