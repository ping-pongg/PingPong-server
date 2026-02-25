package pingpong.backend.domain.swagger.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.swagger.dto.response.EndpointGroupResponse;
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

	@GetMapping("/{teamId}")
	@Operation(summary="swagger JSON 읽어오기",description = "현재 서버의 swagger docs를 불러옵니다.")
	public SuccessResponse<JsonNode> swaggerDiff(
		@PathVariable Long teamId
	) {
		return SuccessResponse.ok(swaggerService.readSwaggerDocs(teamId));
	}


	@GetMapping("/sync/{teamId}")
	@Operation(
		summary = "swagger JSON 정규화해서 DB에 저장",
		description = """
        지정한 서버의 Swagger(OpenAPI) 문서를 조회하여 최신 스냅샷과 비교합니다.
        스펙 해시가 동일한 경우에는 별도의 저장 없이 빈 결과를 반환합니다.
        
        스펙에 변경이 감지되면 다음 작업을 수행합니다:
        - endpoint, request, response, parameter 정보를 정규화하여 저장
        - 이전 스냅샷과 비교하여 endpoint 단위 변경 여부 판별
        - 신규/수정/삭제된 endpoint를 ChangeType 기준으로 구분
        
        최종적으로 변경이 발생한 endpoint만 controller(tag) 단위로 그룹화하여 반환합니다.
        """
	)
	public SuccessResponse<List<EndpointGroupResponse>> compareAndSaveSwagger(
		@PathVariable Long teamId,
		@CurrentMember Member currentMember
	){
		return SuccessResponse.ok(swaggerService.syncSwagger(teamId,currentMember));
	}

}
