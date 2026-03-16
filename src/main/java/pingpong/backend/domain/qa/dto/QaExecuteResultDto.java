package pingpong.backend.domain.qa.dto;

import java.time.LocalDateTime;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "QA 실행 결과")
public record QaExecuteResultDto(

	@Schema(description = "실행 결과 ID")
	Long id,

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
	Long durationMs
) {}
