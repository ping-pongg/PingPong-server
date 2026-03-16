package pingpong.backend.domain.swaggerdiff.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "스키마 변경 상세 정보 (openapi-diff ChangedSchema 기반)")
public record SchemaChangeDto(

	@Schema(description = "변경 전 스키마 (정규화)")
	JsonNode oldSchema,

	@Schema(description = "변경 후 스키마 (정규화)")
	JsonNode newSchema,

	@Schema(description = "추가된 프로퍼티")
	Map<String, JsonNode> addedProperties,

	@Schema(description = "삭제된 프로퍼티")
	Map<String, JsonNode> removedProperties,

	@Schema(description = "변경된 프로퍼티 (재귀)")
	Map<String, SchemaChangeDto> changedProperties,

	@Schema(description = "description 변경 전/후")
	ChangedMetadataDto descriptionChange,

	@Schema(description = "required 필드 리스트 변경")
	ChangedListDto requiredChanges,

	@Schema(description = "enum 값 리스트 변경")
	ChangedListDto enumChanges,

	@Schema(description = "배열 items 스키마 변경 (재귀)")
	SchemaChangeDto itemsChange
) {
}
