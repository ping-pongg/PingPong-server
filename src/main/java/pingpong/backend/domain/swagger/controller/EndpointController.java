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
import pingpong.backend.domain.swagger.dto.EndpointDetailResponse;
import pingpong.backend.domain.swagger.dto.EndpointResponse;
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
	@Operation(summary="endpoint 상세 조회",description = "변경된 사항이 있는 경우 diff까지 함께 조회할 수 있도록 합니다.")
	public SuccessResponse<EndpointDetailResponse> getEndpointDetails(
		@PathVariable Long endpointId
	){
		return SuccessResponse.ok(swaggerService.getEndpointDetails(endpointId));
	}

	@GetMapping("/list/{teamId}")
	@Operation(summary="해당 프로젝트에 속한 모든 엔드포인트 조회")
	public SuccessResponse<List<EndpointResponse>> getEndpointList(
		@PathVariable Long teamId
	){
		return SuccessResponse.ok(endpointService.getEndpointList(teamId));
	}

	@PostMapping("{flowImageId}/endpoint/{endpointId}/complete")
	@Operation(summary = "연동 완료", description = "해당 API를 연동 완료시 요청해주시면 됩니다.")
	public SuccessResponse<Boolean> completeEndpoint(
		@PathVariable Long flowImageId,
		@PathVariable Long endpointId,
		@CurrentMember Member member
	) {
		endpointService.completeEndpoint(flowImageId, endpointId, member);
		return SuccessResponse.ok();
	}
}
