package pingpong.backend.domain.eval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Judge LLM이 반환하는 JSON 전체 구조.
 * 각 지표별로 score(또는 flag) + reason을 독립적으로 보유.
 *
 * 예시 JSON:
 * {
 *   "faithfulness":      {"score": 0.85, "reason": "컨텍스트 95% 반영됨"},
 *   "answer_relevance":  {"score": 0.92, "reason": "질문 핵심 요구에 직접 답변"},
 *   "instruction":       {"score": 1.0,  "reason": "모든 지시 준수"},
 *   "hallucination":     {"score": 0.05, "reason": "소량 추론 포함, 경미"},
 *   "contradiction":     {"flag": false, "reason": "모순 없음"},
 *   "context_recall":    {"score": 0.8,  "reason": "필요 정보 80% 검색됨"},
 *   "context_precision": {"score": 0.6,  "reason": "5개 중 3개 유용"}
 * }
 */
public record JudgeResult(
        @JsonProperty("faithfulness")      JudgeMetricResult faithfulness,
        @JsonProperty("answer_relevance")  JudgeMetricResult answerRelevance,
        @JsonProperty("instruction")       JudgeMetricResult instruction,
        @JsonProperty("hallucination")     JudgeMetricResult hallucination,
        @JsonProperty("contradiction")     JudgeMetricResult contradiction,
        @JsonProperty("context_recall")    JudgeMetricResult contextRecall,
        @JsonProperty("context_precision") JudgeMetricResult contextPrecision
) {}
