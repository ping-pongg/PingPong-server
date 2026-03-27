package pingpong.backend.domain.qaeval.dto;

import java.util.List;

public record SchemaComparisonResult(
	String type,
	int totalCases,
	int positiveTotal,
	int positiveMatch,
	int positiveMismatch,
	double positiveMatchRate,
	int negativeTotal,
	int negativeMatch,
	int negativeMismatch,
	double negativeMatchRate,
	List<SchemaMismatchItem> mismatches
) {
}
