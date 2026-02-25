package pingpong.backend.domain.eval.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pingpong.backend.domain.eval.LlmEvalCase;
import pingpong.backend.domain.eval.enums.EvalStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface LlmEvalCaseRepository extends JpaRepository<LlmEvalCase, Long> {

    // ── 최근 리스트 ────────────────────────────────────────────────────────────
    @Query("SELECT e FROM LlmEvalCase e ORDER BY e.createdAt DESC")
    List<LlmEvalCase> findRecent(Pageable pageable);

    // ── Worst case ────────────────────────────────────────────────────────────
    @Query("SELECT e FROM LlmEvalCase e WHERE e.faithfulnessScore IS NOT NULL ORDER BY e.faithfulnessScore ASC, e.createdAt DESC")
    List<LlmEvalCase> findFaithfulnessWorst(Pageable pageable);

    @Query("SELECT e FROM LlmEvalCase e WHERE e.hallucinationRate IS NOT NULL ORDER BY e.hallucinationRate DESC, e.createdAt DESC")
    List<LlmEvalCase> findHallucinationWorst(Pageable pageable);

    @Query("SELECT e FROM LlmEvalCase e WHERE e.latencyMsTotal IS NOT NULL ORDER BY e.latencyMsTotal DESC, e.createdAt DESC")
    List<LlmEvalCase> findLatencyWorst(Pageable pageable);

    @Query("SELECT e FROM LlmEvalCase e WHERE e.costUsd IS NOT NULL ORDER BY e.costUsd DESC, e.createdAt DESC")
    List<LlmEvalCase> findCostWorst(Pageable pageable);

    @Query("SELECT e FROM LlmEvalCase e WHERE e.contradictionFlag = true ORDER BY e.createdAt DESC")
    List<LlmEvalCase> findContradictionCases(Pageable pageable);

    // ── Summary 집계 ──────────────────────────────────────────────────────────
    @Query("""
            SELECT
                COUNT(e),
                AVG(e.faithfulnessScore),
                MIN(e.faithfulnessScore),
                MAX(e.faithfulnessScore),
                AVG(e.hallucinationRate),
                AVG(e.latencyMsTotal),
                AVG(e.tokensTotal),
                AVG(e.costUsd),
                SUM(CASE WHEN e.evalStatus = :failedStatus THEN 1 ELSE 0 END),
                AVG(e.avgSimilarityScore),
                MIN(e.minSimilarityScore),
                MAX(e.maxSimilarityScore)
            FROM LlmEvalCase e
            WHERE e.createdAt >= :from
            """)
    List<Object[]> findSummaryStats(
            @Param("from") LocalDateTime from,
            @Param("failedStatus") EvalStatus failedStatus
    );

    // ── 시계열 집계 (MySQL native — DATE_FORMAT 사용) ──────────────────────────
    // :fmt → day: '%Y-%m-%d', hour: '%Y-%m-%d %H:00'
    @Query(value = """
            SELECT
                DATE_FORMAT(created_at, :fmt)   AS bucket,
                COUNT(*)                         AS cnt,
                AVG(faithfulness_score)          AS avg_faithfulness,
                AVG(hallucination_rate)          AS avg_hallucination,
                AVG(latency_ms_total)            AS avg_latency_ms,
                AVG(cost_usd)                    AS avg_cost_usd,
                AVG(avg_similarity_score)        AS avg_similarity_score
            FROM llm_eval_case
            WHERE created_at >= :from
            GROUP BY bucket
            ORDER BY bucket ASC
            """, nativeQuery = true)
    List<Object[]> findTimeseries(
            @Param("from") LocalDateTime from,
            @Param("fmt")  String fmt
    );

    // ── Similarity score 구간별 분포 (전체 집계 히스토그램) ────────────────────
    // context_json 내 개별 doc score를 JSON_TABLE로 펼쳐 0.1 단위 구간으로 집계
    @Query(value = """
            SELECT
                FLOOR(jt.score_value * 10) / 10        AS lower_bound,
                FLOOR(jt.score_value * 10) / 10 + 0.1  AS upper_bound,
                COUNT(*)                                AS doc_count
            FROM llm_eval_case c
            CROSS JOIN JSON_TABLE(
                c.context_json, '$[*]'
                COLUMNS (score_value DOUBLE PATH '$.score')
            ) AS jt
            WHERE c.created_at >= :from
              AND c.eval_status != 'FAILED'
              AND c.context_json IS NOT NULL
              AND c.context_json != '[]'
            GROUP BY FLOOR(jt.score_value * 10) / 10, FLOOR(jt.score_value * 10) / 10 + 0.1
            ORDER BY lower_bound ASC
            """, nativeQuery = true)
    List<Object[]> findSimilarityHistogram(@Param("from") LocalDateTime from);
}
