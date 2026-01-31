package pingpong.backend.global.storage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PresignedUrlRequest (

	@Schema(
		description = "이미지 이름",
		example= "minseo.png"
	)
	@NotBlank
	String imageName
){
}
