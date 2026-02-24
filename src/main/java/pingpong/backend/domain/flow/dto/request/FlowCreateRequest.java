package pingpong.backend.domain.flow.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pingpong.backend.global.storage.dto.ImageUploadType;

@Schema(description = "플로우 생성 request")
public record FlowCreateRequest (

	@NotBlank
	@Schema(description = "flow 이름", example = "Login", requiredMode = Schema.RequiredMode.REQUIRED)
	String title,

	@NotBlank
	@Schema(description="flow에 대한 설명",example="로그인 및 회원가입 로직 관련 flow입니다.")
	String description,

	@Schema(description="업로드할 이미지의 확장자 목록")
	List<ImageUploadType> imageTypes

){}
