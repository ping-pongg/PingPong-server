package pingpong.backend.domain.swaggerdiff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.enums.CrudMethod;

import java.util.List;

@Schema(description = "엔드포인트 단건 상세 (openapi-diff 라이브러리 기반)")
public record EndpointDiffDetailDto(

	@Schema(description = "변경 유형 (ADDED / REMOVED / MODIFIED / UNCHANGED)")
	DiffType diffType,

	@Schema(description = "엔드포인트 ID")
	Long endpointId,

	@Schema(description = "태그명")
	String tag,

	@Schema(description = "엔드포인트 경로")
	String path,

	@Schema(description = "HTTP 메서드")
	CrudMethod method,

	@Schema(description = "summary 변경 전/후")
	ChangedMetadataDto summaryChange,

	@Schema(description = "description 변경 전/후")
	ChangedMetadataDto descriptionChange,

	@Schema(description = "Operation ID")
	String operationId,

	@Schema(description = "deprecated 여부")
	boolean deprecated,

	@Schema(description = "파라미터 변경 목록")
	List<ParameterChangeDto> parameterChanges,

	@Schema(description = "요청 본문 변경")
	RequestBodyChangeDto requestBodyChange,

	@Schema(description = "응답 변경 목록")
	List<ResponseChangeDto> responseChanges
) {
}
