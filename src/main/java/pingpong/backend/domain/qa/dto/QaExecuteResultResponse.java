package pingpong.backend.domain.qa.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record QaExecuteResultResponse(
	Long id,
	Long qaCaseId,
	int httpStatus,
	Boolean isSuccess,
	Map<String, String> responseHeaders,
	Object responseBody,
	LocalDateTime executedAt,
	Long durationMs
) {}
