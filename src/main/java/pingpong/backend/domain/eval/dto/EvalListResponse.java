package pingpong.backend.domain.eval.dto;

import pingpong.backend.domain.eval.LlmEvalCase;

import java.time.LocalDateTime;

public record EvalListResponse(
        Long id,
        LocalDateTime createdAt,
        Long teamId,
        Double faithfulnessScore,
        Double hallucinationRate,
        Boolean contradictionFlag,
        Integer latencyMsTotal,
        Integer tokensTotal,
        Double costUsd,
        String evalStatus
) {
    public static EvalListResponse from(LlmEvalCase e) {
        return new EvalListResponse(
                e.getId(), e.getCreatedAt(), e.getTeamId(),
                e.getFaithfulnessScore(), e.getHallucinationRate(), e.getContradictionFlag(),
                e.getLatencyMsTotal(), e.getTokensTotal(), e.getCostUsd(),
                e.getEvalStatus().name()
        );
    }
}
