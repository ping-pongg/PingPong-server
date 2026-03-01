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
import pingpong.backend.domain.flow.FlowImageEndpoint;
import pingpong.backend.domain.flow.enums.UploadStatus;
import pingpong.backend.domain.flow.dto.ImageUploadDto;
import pingpong.backend.domain.flow.dto.request.FlowCreateRequest;
import pingpong.backend.domain.flow.dto.request.FlowEndpointAssignRequest;
import pingpong.backend.domain.flow.dto.response.FlowCreateResponse;
import pingpong.backend.domain.flow.dto.response.FlowEndpointAssignResponse;
import pingpong.backend.domain.flow.dto.response.FlowImageResponse;
import pingpong.backend.domain.flow.dto.response.FlowListItemResponse;
import pingpong.backend.domain.flow.dto.response.FlowResponse;
import pingpong.backend.domain.flow.dto.response.ImageEndpointsResponse;
import pingpong.backend.domain.flow.repository.FlowImageEndpointRepository;
import pingpong.backend.domain.flow.repository.FlowImageRepository;
import pingpong.backend.domain.flow.repository.FlowRepository;
import pingpong.backend.domain.flow.FlowImage;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.server.service.ServerService;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.enums.Role;
import pingpong.backend.domain.team.service.TeamService;
import pingpong.backend.global.exception.CustomException;
import pingpong.backend.global.storage.dto.ImageUploadType;
import pingpong.backend.global.storage.dto.request.PresignedUrlRequest;
import pingpong.backend.global.storage.dto.response.PresignedUrlResponse;
import pingpong.backend.global.storage.service.PresignedUrlService;
import pingpong.backend.domain.flow.FlowErrorCode;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlowService {

	private final TeamService teamService;
	private final FlowRepository flowRepository;
	private final PresignedUrlService presignedUrlService;
	private final FlowImageRepository flowImageRepository;
	private final ServerService serverService;
	private final FlowImageEndpointRepository flowImageEndpointRepository;
	private final EndpointRepository endpointRepository;

	/**
	 * 팀별 flow 목록 조회 (role에 따라 alert 의미 다름)
	 */
	@Transactional(readOnly = true)
	public List<FlowListItemResponse> getFlowList(Long teamId, Role role) {
		// 1. 팀의 모든 flow 조회
		List<Flow> flows = flowRepository.findByTeamId(teamId);
		if (flows.isEmpty()) {
			return List.of();
		}

		List<Long> flowIds = flows.stream().map(Flow::getId).toList();

		// 2. flow별 대표 이미지 batch 조회 (id가 가장 작은 것 1건)
		List<FlowImage> thumbnails = flowImageRepository.findFirstImagePerFlow(flowIds);
		Map<Long, FlowImage> thumbnailByFlowId = thumbnails.stream()
			.collect(Collectors.toMap(fi -> fi.getFlow().getId(), Function.identity()));

		// 3. role별 alert 판단에 필요한 flow id 집합 조회
		// BACKEND: 엔드포인트가 하나라도 할당된 flow id 집합
		// FRONTEND: isLinked=false인 엔드포인트가 하나라도 있는 flow id 집합
		Set<Long> alertFlowIds = switch (role) {
			case BACKEND -> Set.copyOf(flowImageEndpointRepository.findFlowIdsWithAnyEndpoint(flowIds));
			case FRONTEND -> Set.copyOf(flowImageEndpointRepository.findFlowIdsWithUnlinkedEndpoint(flowIds));
			default -> Set.of();
		};

		// 4. 대표 이미지 presigned URL 발급 및 DTO 조립
		return flows.stream().map(flow -> {
			FlowImage thumbnail = thumbnailByFlowId.get(flow.getId());
			String thumbnailUrl = null;
			if (thumbnail != null && thumbnail.getStatus() == UploadStatus.COMPLETE) {
				thumbnailUrl = presignedUrlService.getGetS3Url(thumbnail.getObjectKey()).presignedUrl();
			}

			// BACKEND: true=미할당, false=할당됨
			// FRONTEND: true=미연동 엔드포인트 존재, false=전체 연동 완료 or 엔드포인트 미할당
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
	 * @param request
	 */
	@Transactional
	public FlowCreateResponse createFlow(FlowCreateRequest request,Long teamId) {
		Team team=teamService.getTeam(teamId);
		Flow flow = flowRepository.save(
			Flow.create(request,team)
		);

		List<ImageUploadDto> uploads = new ArrayList<>();

		// 2️이미지 개수만큼 presigned 발급
		for (ImageUploadType type : request.imageTypes()) {

			PresignedUrlResponse presigned =
				presignedUrlService.getPostS3Url(
					new PresignedUrlRequest(type)
				);

			// DB에 FlowImage 저장
			FlowImage image = flowImageRepository.save(
				FlowImage.create(
					flow,
					presigned.objectKey()
				)
			);

			uploads.add(new ImageUploadDto(
				image.getId(),
				presigned.presignedUrl(),
				presigned.objectKey()
			));
		}

		return new FlowCreateResponse(flow.getId(), uploads);
	}

	/**
	 * 단일 flow 상세 조회
	 * @param flowId
	 * @param member
	 * @return
	 */
	@Transactional(readOnly = true)
	public FlowResponse getFlow(Long flowId, Member member) {

		// Flow 조회
		Flow flow = flowRepository.findById(flowId)
			.orElseThrow(() ->
				new CustomException(FlowErrorCode.FLOW_NOT_FOUND)
			);

		//이미지 조회
		List<FlowImage> images =
			flowImageRepository.findByFlowId(flowId);

		// DTO 변환
		List<FlowImageResponse> imageResponses =
			images.stream()
				.map(image -> {

					String imageUrl = null;
					if (image.getStatus() == UploadStatus.COMPLETE) {
						imageUrl = presignedUrlService
							.getGetS3Url(image.getObjectKey()) //조회용 presignedURL
							.presignedUrl();
					}

					return new FlowImageResponse(
						image.getId(),
						imageUrl,
						image.getStatus()
					);
				})
				.toList();

		return new FlowResponse(
			flow.getId(),
			flow.getTitle(),
			flow.getDescription(),
			imageResponses
		);
	}

	/**
	 * flow 내의 image에 할당된 엔드포인트들 전체 조회
	 * @param flowImageId
	 * @param member
	 * @return
	 */
	@Transactional(readOnly = true)
	public List<ImageEndpointsResponse> getImageEndpoints(Long flowImageId,Member member){
		flowImageRepository.findById(flowImageId)
			.orElseThrow(()->new CustomException(FlowErrorCode.FLOW_IMAGE_NOT_FOUND));

		List<FlowImageEndpoint> endpoints=flowImageEndpointRepository.findMappingsByImageId(flowImageId);
		return endpoints.stream()
			.map(ImageEndpointsResponse::from)
			.toList();
	}

	/**
	 * image에 endpoints 할당
	 * @param request
	 * @param flowImageId
	 * @param member
	 * @return
	 */
	@Transactional
	public FlowEndpointAssignResponse assignEndpoints(
		List<FlowEndpointAssignRequest> request,
		Long flowImageId,
		Member member
	) {
		FlowImage image = flowImageRepository.findById(flowImageId)
			.orElseThrow(() -> new CustomException(FlowErrorCode.FLOW_IMAGE_NOT_FOUND));

		// 요청 endpointId들 추출
		List<Long> endpointIds = request.stream()
			.map(FlowEndpointAssignRequest::endpointId)
			.distinct()
			.toList();

		// Endpoint 존재 검증
		List<Endpoint> endpoints = endpointRepository.findAllByIdIn(endpointIds);

		Map<Long, Endpoint> endpointMap = endpoints.stream()
			.collect(Collectors.toMap(Endpoint::getId, Function.identity()));

		// upsert: 있으면 위치 업데이트, 없으면 생성
		for (FlowEndpointAssignRequest r : request) {
			Long endpointId = r.endpointId();
			Endpoint endpoint = endpointMap.get(endpointId);

			if (endpoint == null) {
				throw new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND);
			}

			FlowImageEndpoint mapping = flowImageEndpointRepository
				.findByImageIdAndEndpointId(flowImageId, endpointId)
				.orElseGet(() -> flowImageEndpointRepository.save(
					FlowImageEndpoint.create(image, endpoint, r.x(), r.y())
				));

			// 이미 존재하면 좌표 업데이트
			mapping.updatePosition(r.x(), r.y());
		}

		// 최종 상태 반환
		List<ImageEndpointsResponse> assigned = flowImageEndpointRepository.findAllByImageId(flowImageId).stream()
			.map(ImageEndpointsResponse::from)
			.toList();

		return new FlowEndpointAssignResponse(flowImageId, assigned);
	}
}
