package pingpong.backend.domain.flow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.flow.dto.request.FlowCreateRequest;
import pingpong.backend.domain.flow.dto.request.FlowRequestConnectRequest;
import pingpong.backend.domain.flow.dto.request.FlowRequestCreateRequest;
import pingpong.backend.domain.flow.dto.response.FlowCreateResponse;
import pingpong.backend.domain.flow.dto.response.FlowListItemResponse;
import pingpong.backend.domain.flow.dto.response.FlowRequestResponse;
import pingpong.backend.domain.flow.dto.response.FlowResponse;
import pingpong.backend.domain.flow.dto.response.ImageEndpointsResponse;
import pingpong.backend.domain.flow.service.FlowService;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.team.enums.Role;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/flows")
@Tag(name = "Flow API", description = "flow를 생성/수정/삭제 조회하는 API입니다.")
public class FlowController {

	private final FlowService flowService;

	@GetMapping("/teams/{teamId}")
	@Operation(
		summary = "팀별 Flow 목록 조회",
		description = """
			- 팀에 속한 Flow 목록을 반환합니다. alert 필드의 의미는 role 파라미터에 따라 다릅니다.
			- 각 Flow의 썸네일은 해당 Flow에 속한 이미지 중 가장 먼저 등록된 1장의 presigned URL입니다.
			- 이미지가 없거나 업로드 미완료(PENDING) 상태이면 thumbnailUrl은 null입니다.

			| role | `true` | `false` | `null` |
			|---|---|---|---|
			| `BACKEND` | 엔드포인트 **미할당** (액션 필요) | 엔드포인트 할당됨 | — |
			| `FRONTEND` | **미연동** 엔드포인트 존재 (액션 필요) | 전체 연동 완료 또는 엔드포인트 미할당 | — |
			| `PLANNING` / `QA` | — | — | 항상 `null` |
			"""
	)
	public SuccessResponse<List<FlowListItemResponse>> getFlowList(
		@PathVariable Long teamId,
		@RequestParam Role role
	) {
		return SuccessResponse.ok(flowService.getFlowList(teamId, role));
	}

	@PostMapping("/{teamId}")
	@Operation(summary = "flow 생성", description = "해당 프로젝트의 특정 flow를 생성합니다.")
	public SuccessResponse<FlowCreateResponse> createFlow(
		@PathVariable Long teamId,
		@RequestBody FlowCreateRequest flowCreateRequest
	) {
		return SuccessResponse.ok(flowService.createFlow(flowCreateRequest, teamId));
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
	) {
		return SuccessResponse.ok(flowService.getFlow(flowId, currentMember));
	}

	@PostMapping("/images/{imageId}/requests")
	@Operation(
		summary = "Flow 이미지에 request 생성",
		description = "지정한 Flow 이미지에 요청(내용, 위치 좌표)을 추가합니다. " +
			"생성된 request에 이후 endpoint를 연결할 수 있습니다."
	)
	public SuccessResponse<FlowRequestResponse> createRequest(
		@PathVariable Long imageId,
		@RequestBody FlowRequestCreateRequest request,
		@CurrentMember Member currentMember
	) {
		return SuccessResponse.ok(flowService.createRequest(imageId, request, currentMember));
	}

	@PostMapping("/images/requests/{requestId}/endpoints")
	@Operation(
		summary = "request에 endpoint 연결",
		description = "지정한 request에 endpoint를 연결합니다. " +
			"이미 연결된 경우 중복 저장 없이 무시됩니다. " +
			"연결 후 해당 request의 전체 endpoint 목록을 반환합니다."
	)
	public SuccessResponse<FlowRequestResponse> connectEndpoint(
		@PathVariable Long requestId,
		@RequestBody FlowRequestConnectRequest request,
		@CurrentMember Member currentMember
	) {
		return SuccessResponse.ok(flowService.connectEndpoint(requestId, request, currentMember));
	}

	@GetMapping("/images/{flowImageId}/requests")
	@Operation(
		summary = "Flow 이미지의 request 목록 조회 (request 기준)",
		description = "지정한 Flow 이미지에 등록된 모든 request 목록을 반환합니다. " +
			"각 request의 내용(content), 이미지 내 위치(x, y)와 함께 " +
			"연결된 endpoint 목록(태그, 경로, 메서드, 요약, 연동 상태)을 포함합니다."
	)
	public SuccessResponse<List<FlowRequestResponse>> getFlowRequests(
		@PathVariable Long flowImageId,
		@CurrentMember Member currentMember
	) {
		return SuccessResponse.ok(flowService.getFlowRequests(flowImageId, currentMember));
	}

	@GetMapping("/images/{flowImageId}/endpoints")
	@Operation(
		summary = "Flow 이미지의 endpoint 목록 조회 (endpoint 기준)",
		description = "지정한 Flow 이미지에 연결된 모든 endpoint 목록을 반환합니다. " +
			"각 endpoint의 기본 정보(태그, 경로, 메서드, 요약)와 연동 상태(isChanged, isLinked)와 함께 " +
			"해당 endpoint를 참조하는 request 목록(내용만, 위치 좌표 제외)을 포함합니다."
	)
	public SuccessResponse<List<ImageEndpointsResponse>> getImageEndpoints(
		@PathVariable Long flowImageId,
		@CurrentMember Member currentMember
	) {
		return SuccessResponse.ok(flowService.getImageEndpoints(flowImageId, currentMember));
	}
}
