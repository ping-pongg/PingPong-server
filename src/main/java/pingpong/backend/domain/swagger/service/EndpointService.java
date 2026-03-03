package pingpong.backend.domain.swagger.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.flow.FlowErrorCode;
import pingpong.backend.domain.flow.RequestEndpoint;
import pingpong.backend.domain.flow.enums.FlowEndpointLinkStatus;
import pingpong.backend.domain.flow.repository.FlowImageRepository;
import pingpong.backend.domain.flow.repository.RequestEndpointRepository;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.SwaggerSnapshot;
import pingpong.backend.domain.swagger.dto.response.EndpointResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointStatusResponse;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.swagger.repository.SwaggerSnapshotRepository;
import pingpong.backend.global.exception.CustomException;

@Service
@Slf4j
@RequiredArgsConstructor
public class EndpointService {

	private final RequestEndpointRepository requestEndpointRepository;
	private final EndpointRepository endpointRepository;
	private final SwaggerSnapshotRepository swaggerSnapshotRepository;
	private final FlowImageRepository flowImageRepository;

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
		flowImageRepository.findById(flowImageId)
			.orElseThrow(() -> new CustomException(FlowErrorCode.FLOW_IMAGE_NOT_FOUND));

		endpointRepository.findById(endpointId)
			.orElseThrow(() -> new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND));

		List<RequestEndpoint> links =
			requestEndpointRepository.findByImageIdAndEndpointId(flowImageId, endpointId);

		if (links.isEmpty()) {
			throw new CustomException(SwaggerErrorCode.ENDPOINT_NOT_ASSIGNED);
		}

		links.forEach(RequestEndpoint::markLinked);
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
	 * PM - 개별 엔드포인트 연동 상태 조회
	 */
	@Transactional(readOnly = true)
	public EndpointStatusResponse getEndpointStatus(Long endpointId) {
		Endpoint endpoint = endpointRepository.findById(endpointId)
			.orElseThrow(() -> new CustomException(SwaggerErrorCode.ENDPOINT_NOT_FOUND));

		List<RequestEndpoint> links = requestEndpointRepository.findAllByEndpointId(endpointId);

		FlowEndpointLinkStatus status;

		if (links.isEmpty()) {
			status = FlowEndpointLinkStatus.BACKEND_IN_PROGRESS;
		} else {
			boolean allLinked = links.stream()
				.allMatch(re -> Boolean.TRUE.equals(re.getIsLinked()));

			status = allLinked
				? FlowEndpointLinkStatus.FULLY_INTEGRATED
				: FlowEndpointLinkStatus.FRONTEND_IN_PROGRESS;
		}

		return EndpointStatusResponse.of(endpoint, status);
	}
}
