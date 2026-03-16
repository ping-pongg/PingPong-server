package pingpong.backend.domain.swaggerdiff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문자열 필드의 변경 전/후 값")
public record ChangedMetadataDto(

	@Schema(description = "변경 전 값")
	String oldValue,

	@Schema(description = "변경 후 값")
	String newValue
) {
}
