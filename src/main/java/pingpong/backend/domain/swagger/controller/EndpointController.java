package pingpong.backend.domain.swagger.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.swagger.dto.response.EndpointDetailResponse;
import pingpong.backend.domain.swagger.dto.response.EndpointResponse;
import pingpong.backend.domain.swagger.service.EndpointService;
import pingpong.backend.domain.swagger.service.SwaggerService;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/endpoint")
@Tag(name = "Endpoint API", description = "엔드포인트 조회 및 이미지 내 상태(완료 여부) 관리를 위한 API")
public class EndpointController {

	private final EndpointService endpointService;
	private final SwaggerService swaggerService;

	@GetMapping("/{endpointId}")
	@Operation(summary="endpoint 상세 조회 (미완성)",description = "변경된 사항이 있는 경우 diff까지 함께 조회할 수 있도록 합니다.")
	public SuccessResponse<EndpointDetailResponse> getEndpointDetails(
		@PathVariable Long endpointId
	){
		return SuccessResponse.ok(swaggerService.getEndpointDetails(endpointId));
	}

	@GetMapping("/list/{teamId}")
	@Operation(
		summary = "팀에 속한 전체 엔드포인트 조회",
		description = "지정한 팀(프로젝트)에 등록된 모든 엔드포인트를 조회합니다. " +
			"엔드포인트 매칭 화면 및 검색 화면에서 전체(ALL) 목록을 표시할 때 사용됩니다. " +
			"각 엔드포인트의 태그, 경로, HTTP 메서드, 요약 정보 및 변경 여부를 포함하여 반환합니다."
	)
	public SuccessResponse<List<EndpointResponse>> getEndpointList(
		@PathVariable Long teamId
	){
		return SuccessResponse.ok(endpointService.getEndpointList(teamId));
	}

	@GetMapping("/{teamId}/changed")
	@Operation(
		summary = "팀에 속한 전체 엔드포인트들 중 이번에 바뀐 엔드포인트만 조회(미완성)",
		description = "지정한 팀(프로젝트)에서 변경된 엔드포인트를 조회합니다. " +
			"엔드포인트 매칭 화면에서 NEW 목록을 표시할 때 사용됩니다. "
	)
	public SuccessResponse<List<EndpointResponse>> getChangedEndpointList(
		@PathVariable Long teamId
	){
		return SuccessResponse.ok(endpointService.getEndpointList(teamId));
	}

	@PostMapping("{flowImageId}/endpoint/{endpointId}/complete")
	@Operation(summary = "엔드포인트 연동 상태 완료로 변경", description = "해당 API를 연동 완료시 요청해주시면 됩니다.")
	public SuccessResponse<Boolean> completeEndpoint(
		@PathVariable Long flowImageId,
		@PathVariable Long endpointId,
		@CurrentMember Member member
	) {
		endpointService.completeEndpoint(flowImageId, endpointId, member);
		return SuccessResponse.ok();
	}
}
