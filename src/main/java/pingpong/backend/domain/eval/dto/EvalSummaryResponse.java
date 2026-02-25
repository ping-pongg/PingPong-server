package pingpong.backend.domain.eval.dto;

public record EvalSummaryResponse(
        String range,
        long totalCount,
        long failedCount,
        double failureRate,
        Double avgFaithfulness,
        Double minFaithfulness,
        Double maxFaithfulness,
        Double avgHallucination,
        Double avgLatencyMs,
        Double avgTokensTotal,
        Double avgCostUsd,
        // Similarity
        Double avgSimilarityScore,
        Double minSimilarityScore,
        Double maxSimilarityScore
) {}
