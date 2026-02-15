package pingpong.backend.global.storage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record PresignedUrlResponse (
	@Schema(description = "presigned PUT 업로드 URL")
	String presignedUrl,

	@Schema(description = "업로드 완료 후 접근 가능한 이미지 URL")
	String objectKey
){
}
