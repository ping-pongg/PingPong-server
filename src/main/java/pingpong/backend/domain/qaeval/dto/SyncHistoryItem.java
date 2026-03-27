package pingpong.backend.domain.qaeval.dto;

import java.time.LocalDateTime;

public record SyncHistoryItem(
	Long teamId,
	String teamName,
	String status,
	Integer totalCount,
	Integer successCount,
	Integer failCount,
	String errorMessage,
	LocalDateTime startedAt,
	LocalDateTime completedAt
) {
}
