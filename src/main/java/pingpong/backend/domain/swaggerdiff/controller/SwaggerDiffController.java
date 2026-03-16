package pingpong.backend.domain.swaggerdiff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pingpong.backend.domain.swaggerdiff.dto.EndpointDiffDetailDto;
import pingpong.backend.domain.swaggerdiff.dto.EndpointDiffListResponse;
import pingpong.backend.domain.swaggerdiff.service.SwaggerDiffService;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/swagger-diff")
@Tag(name = "Swagger Diff API", description = "openapi-diff 라이브러리 기반 엔드포인트 변경 비교 API")
public class SwaggerDiffController {

	private final SwaggerDiffService swaggerDiffService;

	@GetMapping("/diff-list")
	@Operation(
		summary = "diff 엔드포인트 리스트 조회",
		description = "두 스냅샷을 비교하여 added/removed/modified/unchanged 엔드포인트를 태그별로 그룹화하여 반환합니다."
	)
	public SuccessResponse<EndpointDiffListResponse> getDiffList(
		@RequestParam Long teamId
	) {
		return SuccessResponse.ok(swaggerDiffService.getDiffList(teamId));
	}

	@GetMapping("/endpoints/{endpointId}")
	@Operation(
		summary = "엔드포인트 단건 조회",
		description = "openapi-diff 기반 EndpointDiffDetailDto를 반환합니다. 응답 내 diffType 필드로 변경 유형을 구분하세요."
	)
	public SuccessResponse<EndpointDiffDetailDto> getEndpointDetail(
			@PathVariable Long endpointId
	) {
		return SuccessResponse.ok(swaggerDiffService.getEndpointUnifiedDetail(endpointId));
	}

}
