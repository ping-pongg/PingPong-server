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
import pingpong.backend.domain.flow.dto.response.FlowResponse;
import pingpong.backend.domain.flow.dto.response.ImageEndpointsResponse;
import pingpong.backend.domain.flow.service.FlowService;
import pingpong.backend.domain.member.Member;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/flows")
@Tag(name="Flow API",description = "flow를 생성/수정/삭제 조회하는 API입니다.")
public class FlowController {

	private final FlowService flowService;

	@PostMapping("/{teamId}")
	@Operation(summary="flow 생성",description = "해당 프로젝트의 특정 flow를 생성합니다.")
	public SuccessResponse<FlowCreateResponse> createFlow(
		@PathVariable Long teamId,
		@RequestBody FlowCreateRequest flowCreateRequest
	){
		return SuccessResponse.ok(flowService.createFlow(flowCreateRequest,teamId));
	}

	@PostMapping("/images/{imageId}/endpoints")
	@Operation(
		summary = "Flow 이미지에 엔드포인트 매핑",
		description = "지정한 Flow 이미지에 하나 이상의 엔드포인트를 할당(매핑)합니다. " +
			"각 엔드포인트는 이미지 내 위치 좌표(x, y)와 함께 저장되며, " +
			"이미 매핑된 경우에는 기존 매핑을 유지한 채 좌표 정보만 업데이트합니다. " +
			"처리 완료 후 해당 이미지에 현재 매핑된 전체 엔드포인트 목록을 반환합니다."
	)
	public SuccessResponse<FlowEndpointAssignResponse> assignEndpoints(
		@PathVariable Long imageId,
		@RequestBody List<FlowEndpointAssignRequest> request,
		@CurrentMember Member currentMember
	){
		return SuccessResponse.ok(flowService.assignEndpoints(request,imageId,currentMember));
	}

	@GetMapping("/images/{flowImageId}/endpoints")
	@Operation(
		summary = "Flow 이미지에 매핑된 엔드포인트 목록 조회",
		description = "지정한 Flow 이미지에 현재 매핑되어 있는 모든 엔드포인트 목록을 조회합니다. " +
			"각 엔드포인트의 기본 정보(태그, 경로, HTTP 메서드, 요약 정보)와 함께 " +
			"이미지 내 위치 좌표(x, y) 및 연동 상태(isLinked, isChanged)를 포함하여 반환합니다. " +
			"요청 시 해당 Flow에 대한 접근 권한을 검증합니다."
	)
	public SuccessResponse<List<ImageEndpointsResponse>> getImageEndpoints(
		@PathVariable Long flowImageId,
		@CurrentMember Member currentMember
	){
		return SuccessResponse.ok(flowService.getImageEndpoints(flowImageId,currentMember));
	}

	@GetMapping("/{flowId}")
	@Operation(
		summary = "Flow 상세 조회",
		description = "지정한 Flow의 상세 정보를 조회합니다. " +
			"Flow의 기본 정보(제목, 설명 등)와 함께 해당 Flow에 속한 이미지 정보들을 반환합니다."
	)
	public SuccessResponse<FlowResponse> getFlow(
		@PathVariable Long flowId,
		@CurrentMember Member currentMember
	){
		return SuccessResponse.ok(flowService.getFlow(flowId,currentMember));
	}



}
