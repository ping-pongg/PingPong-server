package pingpong.backend.domain.server.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.flow.Flow;
import pingpong.backend.domain.flow.dto.request.FlowCreateRequest;
import pingpong.backend.domain.flow.dto.response.FlowCreateResponse;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.server.Server;
import pingpong.backend.domain.server.ServerErrorCode;
import pingpong.backend.domain.server.dto.request.ServerCreateRequest;
import pingpong.backend.domain.server.dto.response.ServerCreateResponse;
import pingpong.backend.domain.server.repository.ServerRepository;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.service.TeamService;
import pingpong.backend.global.exception.CustomException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ServerService {

	private final TeamService teamService;
	private final ServerRepository serverRepository;

	/**
	 * server 생성
	 * @param request
	 * @param teamId
	 * @return
	 */
	public ServerCreateResponse createServer(ServerCreateRequest request,Long teamId){
		Server savedServer=Server.create(request,teamService.getTeam(teamId));
		serverRepository.save(savedServer);
		return new ServerCreateResponse(savedServer.getId());
	}

	/**
	 * Server 조회
	 * @param id
	 * @return
	 */
	public Server getServer(Long id){
		return serverRepository.findById(id)
			.orElseThrow(()->new CustomException(ServerErrorCode.SERVER_NOT_FOUND));
	}

	/**
	 * 특정 유저가 해당 server에 접근할 권한이 있는지 판단
	 * @param serverId
	 * @param teamId
	 * @return
	 */
	public boolean hasMember(Long serverId,Member currentUser){
		return serverRepository.canAccessServer(serverId,currentUser.getId());
	}
}
