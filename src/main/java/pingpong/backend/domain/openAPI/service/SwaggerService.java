package pingpong.backend.domain.openAPI.service;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.team.service.TeamService;

@Service
@Slf4j
@RequiredArgsConstructor
public class SwaggerService {

	private final TeamService teamService;
	private final SwaggerUrlResolver swaggerUrlResolver;
	private final SwaggerParser swaggerParser;

	public JsonNode compareSwaggerDocs(Member member,Long teamId){
		String swaggerJsonUrl=swaggerUrlResolver.resolveSwaggerUrl(teamService.getTeam(teamId).getSwagger());
		return swaggerParser.fetchJson(swaggerJsonUrl);
	}


}
