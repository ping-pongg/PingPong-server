package pingpong.backend.domain.server.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ServerCreateRequest (

	@NotBlank
	@Schema(description = "server 이름", example = "dev", requiredMode = Schema.RequiredMode.REQUIRED)
	String name,

	@NotBlank
	@Schema(description="sever에 대한 설명",example="개발 서버입니다.")
	String description

){}
