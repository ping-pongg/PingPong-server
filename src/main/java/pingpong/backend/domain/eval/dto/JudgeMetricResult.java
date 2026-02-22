package pingpong.backend.domain.eval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Judge LLM이 반환하는 지표 1개의 단위.
 * 수치 지표는 score(0.0~1.0) + reason, contradiction은 flag + reason.
 * reason은 최대 30자 한국어로 제한 (토큰 최적화).
 */
public record JudgeMetricResult(
        @JsonProperty("score")  Double score,
        @JsonProperty("flag")   Boolean flag,
        @JsonProperty("reason") String reason
) {
    public static JudgeMetricResult ofScore(Double score, String reason) {
        return new JudgeMetricResult(score, null, reason);
    }

    public static JudgeMetricResult ofFlag(Boolean flag, String reason) {
        return new JudgeMetricResult(null, flag, reason);
    }
}
