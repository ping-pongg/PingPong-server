package pingpong.backend.domain.qa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.enums.CrudMethod;

@Schema(description = "QA 케이스 요약 정보 (리스트용)")
public record QaCaseSummaryDto(

	@Schema(description = "QA 케이스 ID")
	Long qaId,

	@Schema(description = "엔드포인트 ID")
	Long endpointId,

	@Schema(description = "태그명")
	String tag,

	@Schema(description = "엔드포인트 경로")
	String path,

	@Schema(description = "HTTP 메서드")
	CrudMethod method,

	@Schema(description = "QA 시나리오 이름")
	String scenarioName,

	@Schema(description = "QA 케이스 설명")
	String description,

	@Schema(description = "성공 여부")
	Boolean isSuccess
) {
}
