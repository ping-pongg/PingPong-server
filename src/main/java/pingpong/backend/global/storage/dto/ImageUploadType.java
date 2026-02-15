package pingpong.backend.global.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Schema(description = "이미지 업로드 타입")
public enum ImageUploadType {
	@Schema(description = "PNG 이미지 (image/png)")
	PNG("image/png",".png"),

	@Schema(description = "JPG 이미지 (image/jpeg)")
	JPG("image/jpeg",".jpg"),

	@Schema(description = "JPEG 이미지 (image/jpeg)")
	JPEG("image/jpeg",".jpeg");

	private final String contentType;
	private final String extension;

}
