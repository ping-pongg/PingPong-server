package pingpong.backend.global.storage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pingpong.backend.global.storage.dto.ImageUploadType;

public record PresignedUrlRequest (

	@Schema(
		description = "업로드 이미지 타입",
		example= "PNG",
		allowableValues = {"PNG","JPG","JPEG"}
	)
	@NotNull
	ImageUploadType uploadType
){
}
