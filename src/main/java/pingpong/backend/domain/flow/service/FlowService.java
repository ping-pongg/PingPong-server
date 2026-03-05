package pingpong.backend.domain.flow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.flow.Flow;
import pingpong.backend.domain.flow.FlowImage;
import pingpong.backend.domain.flow.FlowRequest;
import pingpong.backend.domain.flow.FlowErrorCode;
import pingpong.backend.domain.flow.RequestEndpoint;
import pingpong.backend.domain.flow.dto.ImageUploadDto;
import pingpong.backend.domain.flow.dto.request.FlowCreateRequest;
import pingpong.backend.domain.flow.dto.request.FlowRequestConnectRequest;
import pingpong.backend.domain.flow.dto.request.FlowRequestCreateRequest;
import pingpong.backend.domain.flow.dto.response.FlowCreateResponse;
import pingpong.backend.domain.flow.dto.response.FlowImageResponse;
import pingpong.backend.domain.flow.dto.response.FlowListItemResponse;
import pingpong.backend.domain.flow.dto.response.FlowRequestResponse;
import pingpong.backend.domain.flow.dto.response.FlowResponse;
import pingpong.backend.domain.flow.dto.response.ImageEndpointsResponse;
import pingpong.backend.domain.flow.dto.response.ImageEndpointsTagGroupResponse;
import pingpong.backend.domain.flow.enums.UploadStatus;
import pingpong.backend.domain.flow.repository.FlowImageRepository;
import pingpong.backend.domain.flow.repository.FlowRepository;
import pingpong.backend.domain.flow.repository.FlowRequestRepository;
import pingpong.backend.domain.flow.repository.RequestEndpointRepository;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.notion.service.NotionFacade;
import pingpong.backend.domain.server.service.ServerService;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.task.repository.FlowTaskRepository;
import pingpong.backend.domain.task.repository.TaskRepository;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.enums.Role;
import pingpong.backend.domain.team.service.TeamService;
import pingpong.backend.global.exception.CustomException;
import pingpong.backend.global.storage.dto.ImageUploadType;
import pingpong.backend.global.storage.dto.request.PresignedUrlRequest;
import pingpong.backend.global.storage.dto.response.PresignedUrlResponse;
import pingpong.backend.global.storage.service.PresignedUrlService;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlowService {

	private final TeamService teamService;
	private final FlowRepository flowRepository;
	private final PresignedUrlService presignedUrlService;
	private final FlowImageRepository flowImageRepository;
	private final ServerService serverService;
	private final FlowRequestRepository flowRequestRepository;
	private final RequestEndpointRepository requestEndpointRepository;
	private final EndpointRepository endpointRepository;
	private final FlowTaskRepository flowTaskRepository;
	private final TaskRepository taskRepository;
	private final NotionFacade notionFacade;

	/**
	 * 팀별 flow 목록 조회 (role에 따라 alert 의미 다름)
	 */
	@Transactional(readOnly = true)
	public List<FlowListItemResponse> getFlowList(Long teamId, Role role) {
		List<Flow> flows = flowRepository.findByTeamId(teamId);
		if (flows.isEmpty()) {
			return List.of();
		}

		List<Long> flowIds = flows.stream().map(Flow::getId).toList();

		// flow별 대표 이미지 batch 조회 (id가 가장 작은 것 1건)
		List<FlowImage> thumbnails = flowImageRepository.findFirstImagePerFlow(flowIds);
		Map<Long, FlowImage> thumbnailByFlowId = thumbnails.stream()
			.collect(Collectors.toMap(fi -> fi.getFlow().getId(), Function.identity()));

		// role별 alert 판단에 필요한 flow id 집합 조회
		// BACKEND: request에 연결된 endpoint가 하나라도 있는 flow id 집합
		// FRONTEND: isLinked=false인 endpoint가 하나라도 있는 flow id 집합
		Set<Long> alertFlowIds = switch (role) {
			case BACKEND -> Set.copyOf(requestEndpointRepository.findFlowIdsWithAnyEndpoint(flowIds));
			case FRONTEND -> Set.copyOf(requestEndpointRepository.findFlowIdsWithUnlinkedEndpoint(flowIds));
			default -> Set.of();
		};

		return flows.stream().map(flow -> {
			FlowImage thumbnail = thumbnailByFlowId.get(flow.getId());
			String thumbnailUrl = null;
			if (thumbnail != null) {
				thumbnailUrl = presignedUrlService.getGetS3Url(thumbnail.getObjectKey()).presignedUrl();
			}

			// BACKEND: true=미할당, false=할당됨
			// FRONTEND: true=미연동 endpoint 존재, false=전체 연동 완료 or endpoint 미할당
			Boolean alert = switch (role) {
				case BACKEND -> !alertFlowIds.contains(flow.getId());
				case FRONTEND -> alertFlowIds.contains(flow.getId());
				default -> null;
			};

			return new FlowListItemResponse(
				flow.getId(),
				flow.getTitle(),
				flow.getDescription(),
				thumbnailUrl,
				alert
			);
		}).toList();
	}

	/**
	 * flow 생성
	 */
	@Transactional
	public FlowCreateResponse createFlow(FlowCreateRequest request, Long teamId) {
		Team team = teamService.getTeam(teamId);
		Flow flow = flowRepository.save(Flow.create(request, team));

		List<ImageUploadDto> uploads = new ArrayList<>();

		for (ImageUploadType type : request.imageTypes()) {
			PresignedUrlResponse presigned =
				presignedUrlService.getPostS3Url(new PresignedUrlRequest(type));

			FlowImage image = flowImageRepository.save(FlowImage.create(flow, presigned.objectKey()));

			uploads.add(new ImageUploadDto(image.getId(), presigned.presignedUrl(), presigned.objectKey()));
		}

		return new FlowCreateResponse(flow.getId(), uploads);
	}

	/**
	 * 단일 flow 상세 조회
	 */
	@Transactional(readOnly = true)
	public FlowResponse getFlow(Long flowId, Member member) {
		Flow flow = flowRepository.findById(flowId)
			.orElseThrow(() -> new CustomException(FlowErrorCode.FLOW_NOT_FOUND));

		List<FlowImage> images = flowImageRepository.findByFlowId(flowId);

		List<FlowImageResponse> imageResponses = images.stream()
			.map(image -> {
				String imageUrl = null;
				// if (image.getStatus() == UploadStatus.COMPLETE) {
				// 	imageUrl = presignedUrlService.getGetS3Url(image.getObjectKey()).presignedUrl();
				// }
				return new FlowImageResponse(image.getId(), imageUrl);
			})
			.toList();

		return new FlowResponse(flow.getId(), flow.getTitle(), flow.getDescription(), imageResponses);
	}

	/**
	 * flow 이미지에 request 생성
	 */
	@Transactional
	public FlowRequestResponse createRequest(Long imageId, FlowRequestCreateRequest request, Member member) {
		FlowImage image = flowImageRepository.findById(imageId)
			.orElseThrow(() -> new CustomException(FlowErrorCode.FLOW_IMAGE_NOT_FOUND));

		FlowRequest saved = flowRequestRepository.save(
			FlowRequest.create(image, request.content(), request.x(), request.y())
		);

		return new FlowRequestResponse(saved.getId(), saved.getContent(), saved.getX(), saved.getY(), List.of());
	}

	/**
	 * request에 endpoint 연결 (bulk upsert)
	 */
	@Transactional
	public FlowRequestResponse connectEndpoint(Long requestId, FlowRequestConnectRequest request, Member member) {
		FlowRequest flowRequest = flowRequestRepository.findById(requestId)
			.orElseThrow(() -> new CustomException(FlowErrorCode.FLOW_REQUEST_NOT_FOUND));

		List<Endpoint> endpoints = endpointRepository.findAllById(request.endpointIds());

		Set<Long> foundIds = endpoints.stream().map(Endpoint::getId).collect(Collectors.toSet());
		List<Long> missingIds = request.endpointIds().stream()
			.filter(id -> !foundIds.contains(id))
			.toList();

		if (!missingIds.isEmpty()) {
			throw new CustomException(SwaggerErrorCode.ENDPOINTS_NOT_FOUND, missingIds);
		}

		for (Endpoint endpoint : endpoints) {
			if (!requestEndpointRepository.existsByRequestIdAndEndpointId(requestId, endpoint.getId())) {
				requestEndpointRepository.save(RequestEndpoint.create(flowRequest, endpoint));
				syncNotionEndpointStatus(endpoint, flowRequest.getImage().getFlow().getId());
			}
		}

		List<RequestEndpoint> links = requestEndpointRepository.findByRequestIdWithEndpoint(requestId);

		List<FlowRequestResponse.EndpointSummary> endpointSummaries = links.stream()
			.map(link -> new FlowRequestResponse.EndpointSummary(
				link.getEndpoint().getId(),
				link.getEndpoint().getTag(),
				link.getEndpoint().getPath(),
				link.getEndpoint().getMethod(),
				link.getEndpoint().getSummary(),
				link.getEndpoint().getIsChanged(),
				link.getIsLinked()
			))
			.toList();

		return new FlowRequestResponse(
			flowRequest.getId(),
			flowRequest.getContent(),
			flowRequest.getX(),
			flowRequest.getY(),
			endpointSummaries
		);
	}

	/**
	 * flow 이미지의 request 목록 조회 (request 기준, x/y/content + endpoints 포함)
	 */
	@Transactional(readOnly = true)
	public List<FlowRequestResponse> getFlowRequests(Long imageId, Member member) {
		flowImageRepository.findById(imageId)
			.orElseThrow(() -> new CustomException(FlowErrorCode.FLOW_IMAGE_NOT_FOUND));

		List<FlowRequest> requests = flowRequestRepository.findByImageId(imageId);

		return requests.stream().map(req -> {
			List<RequestEndpoint> links = requestEndpointRepository.findByRequestIdWithEndpoint(req.getId());

			List<FlowRequestResponse.EndpointSummary> endpointSummaries = links.stream()
				.map(link -> new FlowRequestResponse.EndpointSummary(
					link.getEndpoint().getId(),
					link.getEndpoint().getTag(),
					link.getEndpoint().getPath(),
					link.getEndpoint().getMethod(),
					link.getEndpoint().getSummary(),
					link.getEndpoint().getIsChanged(),
					link.getIsLinked()
				))
				.toList();

			return new FlowRequestResponse(req.getId(), req.getContent(), req.getX(), req.getY(), endpointSummaries);
		}).toList();
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

	/**
	 * flow 이미지의 endpoint 목록 조회 (tag 별 그룹핑, endpoint 기준, requests 포함)
	 */
	@Transactional(readOnly = true)
	public List<ImageEndpointsTagGroupResponse> getImageEndpoints(Long imageId, Member member) {
		flowImageRepository.findById(imageId)
			.orElseThrow(() -> new CustomException(FlowErrorCode.FLOW_IMAGE_NOT_FOUND));

		List<RequestEndpoint> links = requestEndpointRepository.findByImageIdWithAll(imageId);

		// endpoint 기준으로 그룹핑
		Map<Long, List<RequestEndpoint>> byEndpoint = links.stream()
			.collect(Collectors.groupingBy(re -> re.getEndpoint().getId()));

		// tag 기준으로 재그룹핑 (null tag 허용)
		Map<String, List<ImageEndpointsResponse>> byTag = new java.util.HashMap<>();
		for (List<RequestEndpoint> endpointLinks : byEndpoint.values()) {
			RequestEndpoint first = endpointLinks.get(0);
			Endpoint ep = first.getEndpoint();

			List<ImageEndpointsResponse.RequestSummary> requestSummaries = endpointLinks.stream()
				.map(re -> new ImageEndpointsResponse.RequestSummary(
					re.getRequest().getId(),
					re.getRequest().getContent()
				))
				.toList();

			ImageEndpointsResponse response = new ImageEndpointsResponse(
				ep.getId(),
				ep.getPath(),
				ep.getMethod(),
				ep.getSummary(),
				ep.getIsChanged(),
				first.getIsLinked(),
				requestSummaries
			);

			byTag.computeIfAbsent(ep.getTag(), k -> new ArrayList<>()).add(response);
		}

		return byTag.entrySet().stream()
			.map(entry -> new ImageEndpointsTagGroupResponse(entry.getKey(), entry.getValue()))
			.toList();
	}
}
