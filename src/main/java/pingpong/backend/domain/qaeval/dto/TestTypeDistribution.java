package pingpong.backend.domain.qaeval.dto;

public record TestTypeDistribution(
	int positiveCount,
	int negativeCount,
	double positiveRate,
	double negativeRate
) {
}
