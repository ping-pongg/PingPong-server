package pingpong.backend.domain.eval.dto;

public record EvalTimeseriesResponse(
        String bucket,           // "2026-02-16" (day) 또는 "2026-02-16 14:00" (hour)
        long count,
        Double avgFaithfulness,
        Double avgHallucination,
        Double avgLatencyMs,
        Double avgCostUsd,
        Double avgSimilarityScore
) {}
