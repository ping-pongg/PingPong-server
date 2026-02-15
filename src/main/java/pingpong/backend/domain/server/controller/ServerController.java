package pingpong.backend.domain.server.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.flow.dto.request.FlowCreateRequest;
import pingpong.backend.domain.flow.dto.response.FlowCreateResponse;
import pingpong.backend.domain.server.dto.request.ServerCreateRequest;
import pingpong.backend.domain.server.dto.response.ServerCreateResponse;
import pingpong.backend.domain.server.service.ServerService;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/server")
@Tag(name="server 관련 API",description = "server를 생성/수정/삭제 조회하는 API입니다.")
public class ServerController {

	private final ServerService serverService;

	@PostMapping("/create/{teamId}")
	@Operation(summary="flow 생성",description = "해당 프로젝트의 특정 flow를 생성합니다.")
	public SuccessResponse<ServerCreateResponse> createServer(
		@PathVariable Long teamId,
		@RequestBody ServerCreateRequest serverCreateRequest
	){
		return SuccessResponse.ok(serverService.createServer(serverCreateRequest, teamId));
	}

}
