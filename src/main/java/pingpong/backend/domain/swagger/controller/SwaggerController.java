package pingpong.backend.domain.swagger.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.swagger.SwaggerSnapshot;
import pingpong.backend.domain.swagger.service.SwaggerService;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/swagger")
@Tag(name="JSON 형태의 swagger 저장 API",description = "JSON 형태의 swagger를 불러와서 정규화 후 저장합니다.")
public class SwaggerController {

	private final SwaggerService swaggerService;

	@GetMapping("/{serverId}")
	@Operation(summary="swagger JSON 읽어오기",description = "현재 서버의 swagger docs를 불러옵니다.")
	public SuccessResponse<JsonNode> swaggerDiff(
		@PathVariable Long serverId,
		@CurrentMember Member currentMember
	) {
		return SuccessResponse.ok(swaggerService.readSwaggerDocs(currentMember,serverId));
	}

	@GetMapping("/sync/{serverId}")
	@Operation(summary="swagger JSON 정규화해서 DB에 저장",
		description = "기존에 존재하던 버전의 swagger 내용과 차이점을 비교하여 다를 경우 새로운 버전의 내용의 swagger JSON을 정규화하여 DB에 저장합니다.")
	public SuccessResponse<SwaggerSnapshot> compareAndSaveSwagger(
		@PathVariable Long serverId
	) {
		return SuccessResponse.ok(swaggerService.syncSwagger(serverId));
	}



}
