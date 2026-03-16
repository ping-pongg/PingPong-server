package pingpong.backend.domain.swaggerdiff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "파라미터 변경 상세 (openapi-diff ChangedParameter 기반)")
public record ParameterChangeDto(

	@Schema(description = "변경 유형")
	DiffType diffType,

	@Schema(description = "변경 전 파라미터")
	EndpointParameterDto before,

	@Schema(description = "변경 후 파라미터")
	EndpointParameterDto after,

	@Schema(description = "deprecated 여부")
	boolean deprecated,

	@Schema(description = "description 변경 전/후")
	ChangedMetadataDto descriptionChange,

	@Schema(description = "스키마 변경 상세")
	SchemaChangeDto schemaChange
) {
}
