package pingpong.backend.domain.flow.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record FlowCreateRequest (

	@NotBlank
	@Schema(description = "flow 이름", example = "Login", requiredMode = Schema.RequiredMode.REQUIRED)
	String title,

	@NotBlank
	@Schema(description="flow에 대한 설명",example="로그인 및 회원가입 로직 관련 flow입니다.")
	String description

){}
