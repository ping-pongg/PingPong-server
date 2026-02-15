package pingpong.backend.domain.openAPI.controller;

import org.springdoc.core.service.OpenAPIService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.openAPI.service.SwaggerService;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/swagger")
@Tag(name="JSON 형태의 swagger 저장 API",description = "JSON 형태의 swagger를 불러와서 정규화 후 저장합니다.")
public class SwaggerReadController {

	private final SwaggerService swaggerService;

	@GetMapping("/{teamId}")
	@Operation(summary="swagger 불러온 후 diff 계산",description = "현재 서버의 swagger docs를 불러옵니다.")
	public SuccessResponse<JsonNode> swaggerDiff(
		@PathVariable Long teamId,
		@CurrentMember Member currentMember
	) {
		return SuccessResponse.ok(swaggerService.compareSwaggerDocs(currentMember,teamId));
	}

}
