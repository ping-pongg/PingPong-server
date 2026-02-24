package pingpong.backend.domain.flow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.flow.dto.request.FlowCreateRequest;
import pingpong.backend.domain.flow.dto.request.FlowEndpointAssignRequest;
import pingpong.backend.domain.flow.dto.response.FlowCreateResponse;
import pingpong.backend.domain.flow.dto.response.FlowEndpointAssignResponse;
import pingpong.backend.domain.flow.service.FlowService;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.swagger.dto.EndpointResponse;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/flow")
@Tag(name="flow 관련 API",description = "flow를 생성/수정/삭제 조회하는 API입니다.")
public class FlowController {

	private final FlowService flowService;

	@PostMapping("/create/{teamId}")
	@Operation(summary="flow 생성",description = "해당 프로젝트의 특정 flow를 생성합니다.")
	public SuccessResponse<FlowCreateResponse> createFlow(
		@PathVariable Long teamId,
		@RequestBody FlowCreateRequest flowCreateRequest
	){
		return SuccessResponse.ok(flowService.createFlow(flowCreateRequest,teamId));
	}

	@PostMapping("/api-endpoint/{teamId}")
	@Operation(summary="api endpoint를 해당 flow에 할당")
	public SuccessResponse<FlowEndpointAssignResponse> assignEndpoints(
		@PathVariable Long teamId,
		@CurrentMember Member currentMember,
		@RequestBody List<FlowEndpointAssignRequest> request
	){
		return SuccessResponse.ok(flowService.assignEndpoints(request,teamId));
	}




}
