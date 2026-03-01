package pingpong.backend.domain.test;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "SwaggerDiffDummy", description = "Swagger diff 계산 테스트용 더미 API")
@RestController
@RequestMapping("/api/v1/dummy")
@Validated
public class TestController {

	// =========================
	// 1) GET: path/query/header/cookie + 200/404
	// =========================
	@GetMapping("/users/{userId}")
	@Operation(
		summary = "유저 단건 조회(더미)",
		description = "오예오예",
		responses = {
			@ApiResponse(
				responseCode = "200",
				description = "성공",
				content = @Content(mediaType = "application/json",
					schema = @Schema(implementation = UserResponse.class))
			),
			@ApiResponse(responseCode = "404", description = "유저 없음")
		}
	)
	public ResponseEntity<UserResponse> getUser(
		@PathVariable
		@Parameter(description = "유저 ID", required = true, example = "1001")
		Long userId,

		@RequestParam(required = false)
		@Parameter(description = "상세 포함 여부", example = "true")
		Boolean includeDetail,

		@RequestParam
		@Parameter(description = "집가고싶다")
		Boolean goHome,

		@RequestHeader(value = "X-Trace-Id", required = false)
		@Parameter(in = ParameterIn.HEADER, description = "추적용 Trace ID", example = "trace-abc-123")
		String traceId,

		@CookieValue(value = "SESSION", required = false)
		@Parameter(in = ParameterIn.COOKIE, description = "세션 쿠키", example = "s123")
		String session
	) {
		// 더미 응답
		UserResponse res = new UserResponse(
			userId,
			"dummy-user",
			includeDetail != null && includeDetail,
			traceId,
			session,
			Instant.now().toString()
		);

		// 예시로 404 케이스도 만들고 싶으면 userId 조건으로 분기
		if (userId != null && userId < 0) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		return ResponseEntity.ok(res);
	}

}
