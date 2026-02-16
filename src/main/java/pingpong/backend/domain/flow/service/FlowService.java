package pingpong.backend.domain.flow.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.flow.Flow;
import pingpong.backend.domain.flow.dto.request.FlowCreateRequest;
import pingpong.backend.domain.flow.dto.response.FlowCreateResponse;
import pingpong.backend.domain.flow.repository.FlowRepository;
import pingpong.backend.domain.server.service.ServerService;
import pingpong.backend.domain.team.service.TeamService;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlowService {

	private final ServerService serverService;
	private final FlowRepository flowRepository;

	/**
	 * flow 생성
	 * @param request
	 * @param serverId
	 */
	public FlowCreateResponse createFlow(FlowCreateRequest request,Long serverId){
		Flow savedFlow=Flow.create(request,serverService.getServer(serverId));
		flowRepository.save(savedFlow);
		return new FlowCreateResponse(savedFlow.getId());
	}
}
