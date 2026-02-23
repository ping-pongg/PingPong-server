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
                SUM(CASE WHEN e.evalStatus = :failedStatus THEN 1 ELSE 0 END)
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
                AVG(cost_usd)                    AS avg_cost_usd
            FROM llm_eval_case
            WHERE created_at >= :from
            GROUP BY bucket
            ORDER BY bucket ASC
            """, nativeQuery = true)
    List<Object[]> findTimeseries(
            @Param("from") LocalDateTime from,
            @Param("fmt")  String fmt
    );
}
