package pingpong.backend.domain.swagger.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.flow.FlowErrorCode;
import pingpong.backend.domain.flow.FlowImageEndpoint;
import pingpong.backend.domain.flow.repository.FlowImageEndpointRepository;
import pingpong.backend.domain.flow.repository.FlowImageRepository;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.SwaggerSnapshot;
import pingpong.backend.domain.swagger.dto.response.EndpointResponse;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.swagger.repository.SwaggerSnapshotRepository;
import pingpong.backend.global.exception.CustomException;

@Service
@Slf4j
@RequiredArgsConstructor
public class EndpointService {

	private final FlowImageEndpointRepository flowImageEndpointRepository;
	private final EndpointRepository endpointRepository;
	private final SwaggerSnapshotRepository swaggerSnapshotRepository;
	private final FlowImageRepository flowImageRepository;

	/**
	 * 해당 프로젝트에 속한 모든 엔드포인트 조회
	 * @param teamId
	 * @return
	 */
	@Transactional(readOnly = true)
	public List<EndpointResponse> getEndpointList(Long teamId){
		//기존 최신 스냅샷 조회
		Optional<SwaggerSnapshot> latest=swaggerSnapshotRepository.findTopByTeamIdOrderByIdDesc(teamId);
		if(latest.isEmpty()){
			return List.of();
		}
		Long snapshotId = latest.get().getId();

		List<Endpoint> endpoints=endpointRepository.findBySnapshotId(snapshotId);
		return endpoints.stream()
			.map(EndpointResponse::toDto)
			.collect(Collectors.toList());
	}

	/**
	 * 해당 API를 연동 완료
	 * @param flowImageId
	 * @param endpointId
	 * @param member
	 */
	@Transactional
	public void completeEndpoint(Long flowImageId, Long endpointId, Member member) {

		flowImageRepository.findById(flowImageId)
			.orElseThrow(() -> new CustomException(FlowErrorCode.FLOW_IMAGE_NOT_FOUND));

		FlowImageEndpoint mapping =
			flowImageEndpointRepository
				.findByImageIdAndEndpointId(flowImageId, endpointId)
				.orElseThrow(() -> new CustomException(SwaggerErrorCode.ENDPOINT_NOT_ASSIGNED));

	}

	@Transactional
	public void unlinkChangedEndpoints(List<Endpoint> changedEndpoints) {
		List<Long> endpointIds = changedEndpoints.stream()
			.map(Endpoint::getId)
			.toList();

		if (endpointIds.isEmpty()) {
			return;
		}
		flowImageEndpointRepository.unlinkChangedEndpoints(endpointIds);
	}


}
