package pingpong.backend.domain.test;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유저 생성 요청")
public record UserCreateRequest(

	@Schema(description = "유저 이름", example = "minseo")
	String name,

	@Schema(description = "유저 역할", example = "ADMIN")
	String role,

	@Schema(description = "유저 역할", example = "ADMIN")
	String description
) {}
