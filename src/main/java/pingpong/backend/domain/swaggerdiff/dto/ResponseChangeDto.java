package pingpong.backend.domain.swaggerdiff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "응답 변경 상세 (openapi-diff ChangedApiResponse/ChangedResponse 기반)")
public record ResponseChangeDto(

	@Schema(description = "변경 유형 (상태 코드 단위)")
	DiffType diffType,

	@Schema(description = "HTTP 상태 코드")
	String statusCode,

	@Schema(description = "description 변경 전/후")
	ChangedMetadataDto descriptionChange,

	@Schema(description = "미디어 타입별 콘텐츠 변경 목록")
	List<ContentChangeDto> contentChanges
) {
}
