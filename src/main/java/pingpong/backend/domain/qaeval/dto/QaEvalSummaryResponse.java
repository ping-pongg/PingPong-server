package pingpong.backend.domain.qaeval.dto;

import java.util.List;

public record QaEvalSummaryResponse(
	SchemaComparisonResult bodyComparison,
	SchemaComparisonResult queryParamComparison,
	ExecutionSummaryResponse executionSummary,
	List<SyncHistoryItem> syncHistories,
	TestTypeDistribution testTypeDistribution
) {
}
