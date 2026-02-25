package pingpong.backend.domain.eval.dto;

public record EvalSimilarityHistogramResponse(
        double lowerBound,   // 구간 하한 (예: 0.1)
        double upperBound,   // 구간 상한 (예: 0.2)
        long   docCount      // 해당 구간에 속하는 doc 수
) {}
