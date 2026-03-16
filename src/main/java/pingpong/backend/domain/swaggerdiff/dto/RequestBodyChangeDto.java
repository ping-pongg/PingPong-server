package pingpong.backend.domain.swaggerdiff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "요청 본문 변경 상세 (openapi-diff ChangedRequestBody 기반)")
public record RequestBodyChangeDto(

	@Schema(description = "변경 전 required 값")
	Boolean oldRequired,

	@Schema(description = "변경 후 required 값")
	Boolean newRequired,

	@Schema(description = "description 변경 전/후")
	ChangedMetadataDto descriptionChange,

	@Schema(description = "미디어 타입별 콘텐츠 변경 목록")
	List<ContentChangeDto> contentChanges
) {
}
