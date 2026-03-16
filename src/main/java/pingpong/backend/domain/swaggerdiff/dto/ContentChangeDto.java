package pingpong.backend.domain.swaggerdiff.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "미디어 타입별 콘텐츠 변경 상세 (openapi-diff ChangedContent/ChangedMediaType 기반)")
public record ContentChangeDto(

	@Schema(description = "변경 유형")
	DiffType diffType,

	@Schema(description = "미디어 타입 (예: application/json)")
	String mediaType,

	@Schema(description = "변경 전 스키마")
	JsonNode oldSchema,

	@Schema(description = "변경 후 스키마")
	JsonNode newSchema,

	@Schema(description = "스키마 변경 상세 (MODIFIED인 경우)")
	SchemaChangeDto schemaChange
) {
}
