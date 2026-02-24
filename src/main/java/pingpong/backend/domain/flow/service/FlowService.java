package pingpong.backend.domain.flow.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.flow.Flow;
import pingpong.backend.domain.flow.dto.ImageUploadDto;
import pingpong.backend.domain.flow.dto.request.FlowCreateRequest;
import pingpong.backend.domain.flow.dto.request.FlowEndpointAssignRequest;
import pingpong.backend.domain.flow.dto.response.FlowCreateResponse;
import pingpong.backend.domain.flow.dto.response.FlowEndpointAssignResponse;
import pingpong.backend.domain.flow.repository.FlowImageRepository;
import pingpong.backend.domain.flow.repository.FlowRepository;
import pingpong.backend.domain.flow.FlowImage;
import pingpong.backend.domain.team.Team;
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


	public FlowEndpointAssignResponse assignEndpoints(
		List<FlowEndpointAssignRequest> request,
		Long teamId
	){

	}
}
