package pingpong.backend.domain.qa.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.qa.QaExecuteResult;

@Schema(description = "QA 실행 결과")
public record QaExecuteResultDto(

	@Schema(description = "실행 결과 ID")
	Long qaExecuteId,

	@Schema(description = "HTTP 상태 코드")
	int httpStatus,

	@Schema(description = "성공 여부")
	Boolean isSuccess,

	@Schema(description = "응답 헤더")
	Map<String, String> responseHeaders,

	@Schema(description = "응답 본문")
	Object responseBody,

	@Schema(description = "실행 시각")
	LocalDateTime executedAt,

	@Schema(description = "응답 시간 (ms)")
	Long durationMs,

	@Schema
	int expectedStatusCode
) {
	// QaExecuteResultDto.java 내부에 정적 메서드로 추가
	public static QaExecuteResultDto fromEntity(
		QaExecuteResult result,
		Map<String, String> actualHeaders,
		JsonNode actualBody,
		int httpStatus
	) {
		return new QaExecuteResultDto(
			result.getId(),
			result.getHttpStatus(),
			result.getIsSuccess(),
			null,
			actualBody,
			result.getExecutedAt(),
			result.getDurationMs(),
			httpStatus
		);
	}
}
