package pingpong.backend.domain.qaeval.dto;

import java.util.List;

public record ExecutionSummaryResponse(
	int totalCases,
	int successCount,
	int failCount,
	double successRate,
	List<FailedCaseItem> failedCases
) {
}
