package pingpong.backend.domain.eval.dto;

import pingpong.backend.domain.eval.LlmEvalCase;

import java.time.LocalDateTime;

public record EvalDetailResponse(
        Long id,
        LocalDateTime createdAt,
        String requestId,
        Long teamId,
        String modelName,
        String questionText,
        String answerText,
        String contextJson,
        String judgeReasonJson,
        // Retrieval
        Double contextRecall,
        Double contextPrecision,
        // Generation (모두 0.0~1.0)
        Double faithfulnessScore,
        Double answerRelevanceScore,
        Double instructionScore,
        Double hallucinationRate,
        Boolean contradictionFlag,
        // 운영
        Integer latencyMsTotal,
        Integer latencyMsRetrieval,
        Integer latencyMsGeneration,
        Integer latencyMsEval,
        Integer tokensIn,
        Integer tokensOut,
        Integer tokensCached,
        Integer tokensTotal,
        Double costUsd,
        // Similarity
        Integer retrievedDocCount,
        Double avgSimilarityScore,
        Double minSimilarityScore,
        Double maxSimilarityScore,
        String evalStatus,
        String evalError
) {
    public static EvalDetailResponse from(LlmEvalCase e) {
        return new EvalDetailResponse(
                e.getId(), e.getCreatedAt(), e.getRequestId(), e.getTeamId(), e.getModelName(),
                e.getQuestionText(), e.getAnswerText(), e.getContextJson(), e.getJudgeReasonJson(),
                e.getContextRecall(), e.getContextPrecision(),
                e.getFaithfulnessScore(), e.getAnswerRelevanceScore(), e.getInstructionScore(),
                e.getHallucinationRate(), e.getContradictionFlag(),
                e.getLatencyMsTotal(), e.getLatencyMsRetrieval(), e.getLatencyMsGeneration(), e.getLatencyMsEval(),
                e.getTokensIn(), e.getTokensOut(), e.getTokensCached(), e.getTokensTotal(),
                e.getCostUsd(),
                e.getRetrievedDocCount(), e.getAvgSimilarityScore(), e.getMinSimilarityScore(), e.getMaxSimilarityScore(),
                e.getEvalStatus().name(), e.getEvalError()
        );
    }
}
