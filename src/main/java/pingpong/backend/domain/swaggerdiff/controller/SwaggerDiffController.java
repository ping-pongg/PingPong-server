package pingpong.backend.domain.swaggerdiff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pingpong.backend.domain.swagger.dto.response.EndpointDiffDetailResponse;
import pingpong.backend.domain.swaggerdiff.service.SwaggerDiffService;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/swagger-diff")
@Tag(name = "Swagger Diff API", description = "openapi-diff 라이브러리 기반 엔드포인트 변경 비교 API")
public class SwaggerDiffController {

	private final SwaggerDiffService swaggerDiffService;

	@GetMapping("/endpoints/{endpointId}")
	@Operation(
		summary = "openapi-diff 기반 endpoint diff 조회",
		description = "두 스냅샷의 원본 OpenAPI spec을 openapi-diff 라이브러리로 비교하여 변경 사항을 반환합니다."
	)
	public SuccessResponse<EndpointDiffDetailResponse> getEndpointDiffDetails(
		@PathVariable Long endpointId
	) {
		return SuccessResponse.ok(swaggerDiffService.getEndpointDiffDetails(endpointId));
	}
}
