package pingpong.backend.domain.eval;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import pingpong.backend.domain.eval.enums.EvalStatus;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "llm_eval_case",
        indexes = {
                @Index(name = "idx_llm_eval_case_created_at",  columnList = "created_at"),
                @Index(name = "idx_llm_eval_case_faithfulness", columnList = "faithfulness_score"),
                @Index(name = "idx_llm_eval_case_hallu",        columnList = "hallucination_rate"),
                @Index(name = "idx_llm_eval_case_latency",      columnList = "latency_ms_total"),
                @Index(name = "idx_llm_eval_case_team_created", columnList = "team_id, created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_llm_eval_case_request_id", columnNames = "request_id")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LlmEvalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "llm_eval_case_id")
    private Long id;

    // ── 메타 ──────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime createdAt;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "model_name", length = 64)
    private String modelName;

    // ── 원문 ──────────────────────────────────────────────
    @Column(name = "question_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String questionText;

    @Column(name = "answer_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String answerText;

    // topK 청크 목록: [{id, score, sourceKey, preview}]
    @Column(name = "context_json", columnDefinition = "JSON")
    private String contextJson;

    // Judge LLM 전체 응답 (파싱 성공 시 구조화 JSON, 실패 시 raw 문자열 보존)
    @Column(name = "judge_reason_json", columnDefinition = "JSON")
    private String judgeReasonJson;

    // ── Retrieval 지표 (0.0 ~ 1.0) ───────────────────────
    @Column(name = "context_recall")
    private Double contextRecall;

    @Column(name = "context_precision")
    private Double contextPrecision;

    // ── Generation/품질 지표 (모두 0.0 ~ 1.0으로 통일) ───
    @Column(name = "faithfulness_score")
    private Double faithfulnessScore;

    @Column(name = "answer_relevance_score")
    private Double answerRelevanceScore;

    @Column(name = "instruction_score")
    private Double instructionScore;

    @Column(name = "hallucination_rate")
    private Double hallucinationRate;

    // Boolean 유지: 이진 판단 (정도가 아닌 존재 여부)
    @Column(name = "contradiction_flag", nullable = false)
    @Builder.Default
    private Boolean contradictionFlag = false;

    // ── 운영 지표 ─────────────────────────────────────────
    @Column(name = "latency_ms_total")
    private Integer latencyMsTotal;

    @Column(name = "latency_ms_retrieval")
    private Integer latencyMsRetrieval;

    @Column(name = "latency_ms_generation")
    private Integer latencyMsGeneration;

    @Column(name = "latency_ms_eval")
    private Integer latencyMsEval;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "tokens_cached")
    private Integer tokensCached;

    @Column(name = "tokens_total")
    private Integer tokensTotal;

    @Column(name = "cost_usd")
    private Double costUsd;

    // ── Similarity 지표 ───────────────────────────────────
    @Column(name = "retrieved_doc_count")
    private Integer retrievedDocCount;

    @Column(name = "avg_similarity_score")
    private Double avgSimilarityScore;

    @Column(name = "min_similarity_score")
    private Double minSimilarityScore;

    @Column(name = "max_similarity_score")
    private Double maxSimilarityScore;

    // ── 상태 ──────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "eval_status", nullable = false, length = 16)
    @Builder.Default
    private EvalStatus evalStatus = EvalStatus.DONE;

    @Column(name = "eval_error", columnDefinition = "TEXT")
    private String evalError;
}
