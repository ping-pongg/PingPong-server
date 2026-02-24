package pingpong.backend.domain.swagger.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerSnapshot;
import pingpong.backend.domain.swagger.dto.EndpointResponse;
import pingpong.backend.domain.swagger.repository.EndpointRepository;
import pingpong.backend.domain.swagger.repository.SwaggerSnapshotRepository;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.service.TeamService;

@Service
@Slf4j
@RequiredArgsConstructor
public class EndpointService {

	private final TeamService teamService;
	private final EndpointRepository endpointRepository;
	private final SwaggerSnapshotRepository swaggerSnapshotRepository;

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
}
