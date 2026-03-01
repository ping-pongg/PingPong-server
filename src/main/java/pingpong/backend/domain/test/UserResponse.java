package pingpong.backend.domain.test;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유저 응답 DTO")
public record UserResponse(
	@Schema(example = "1001") Long id,
	@Schema(example = "dummy-user") String name,
	@Schema(description = "상세 포함 여부", example = "true") boolean detailed,
	@Schema(description = "trace id", example = "trace-abc-123") String traceId,
	@Schema(description = "session cookie", example = "s123") String session,
	@Schema(description = "응답 생성 시각", example = "2026-03-01T09:00:00Z") String generatedAt
) {}