package pingpong.backend.domain.eval.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import pingpong.backend.domain.eval.dto.*;
import pingpong.backend.domain.eval.LlmEvalCase;
import pingpong.backend.domain.eval.error.EvalErrorCode;
import pingpong.backend.domain.eval.enums.EvalStatus;
import pingpong.backend.domain.eval.repository.LlmEvalCaseRepository;
import pingpong.backend.global.exception.CustomException;
import pingpong.backend.global.response.result.SuccessResponse;

import java.time.LocalDateTime;
import java.util.List;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/evals")
public class EvalController {

    private final LlmEvalCaseRepository repository;

    /**
     * GET /internal/evals/{id}
     * 평가 케이스 1건 상세 조회 (질문/답변/컨텍스트/모든 지표/judge 근거 포함)
     */
    @GetMapping("/{id}")
    public SuccessResponse<EvalDetailResponse> getDetail(@PathVariable Long id) {
        LlmEvalCase e = repository.findById(id)
                .orElseThrow(() -> new CustomException(EvalErrorCode.EVAL_NOT_FOUND));
        return SuccessResponse.ok(EvalDetailResponse.from(e));
    }

    /**
     * GET /internal/evals?limit=50&offset=0
     * 최근 평가 목록 (경량 DTO, 생성일시 내림차순)
     */
    @GetMapping
    public SuccessResponse<List<EvalListResponse>> getList(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0")  int offset
    ) {
        List<EvalListResponse> result = repository.findRecent(PageRequest.of(offset / limit, limit))
                .stream().map(EvalListResponse::from).toList();
        return SuccessResponse.ok(result);
    }

    /**
     * GET /internal/evals/worst?metric=faithfulness&limit=20
     * Worst case 조회
     * metric: faithfulness | hallucination | latency | cost | contradiction
     */
    @GetMapping("/worst")
    public SuccessResponse<List<EvalListResponse>> getWorst(
            @RequestParam String metric,
            @RequestParam(defaultValue = "20") int limit
    ) {
        PageRequest p = PageRequest.of(0, limit);
        List<LlmEvalCase> cases = switch (metric) {
            case "faithfulness"  -> repository.findFaithfulnessWorst(p);
            case "hallucination" -> repository.findHallucinationWorst(p);
            case "latency"       -> repository.findLatencyWorst(p);
            case "cost"          -> repository.findCostWorst(p);
            case "contradiction" -> repository.findContradictionCases(p);
            default -> throw new CustomException(EvalErrorCode.INVALID_METRIC);
        };
        return SuccessResponse.ok(cases.stream().map(EvalListResponse::from).toList());
    }

    /**
     * GET /internal/evals/summary?range=24h|7d|30d
     * 기간별 KPI 집계 (평균/최솟값/최댓값, 실패율 등)
     */
    @GetMapping("/summary")
    public SuccessResponse<EvalSummaryResponse> getSummary(
            @RequestParam(defaultValue = "7d") String range
    ) {
        List<Object[]> rows = repository.findSummaryStats(rangeToFrom(range), EvalStatus.FAILED);
        Object[] s = rows.isEmpty() ? new Object[12] : rows.get(0);
        long totalCount  = s[0] != null ? ((Number) s[0]).longValue() : 0L;
        long failedCount = s[8] != null ? ((Number) s[8]).longValue() : 0L;
        return SuccessResponse.ok(new EvalSummaryResponse(
                range, totalCount, failedCount,
                totalCount > 0 ? (double) failedCount / totalCount : 0.0,
                nullableDouble(s[1]), nullableDouble(s[2]), nullableDouble(s[3]),
                nullableDouble(s[4]), nullableDouble(s[5]), nullableDouble(s[6]), nullableDouble(s[7]),
                nullableDouble(s[9]), nullableDouble(s[10]), nullableDouble(s[11])
        ));
    }

    /**
     * GET /internal/evals/timeseries?range=7d&interval=day
     * 날짜/시간 단위 지표 집계 (시계열 그래프용)
     * interval: day (기본) | hour
     */
    @GetMapping("/timeseries")
    public SuccessResponse<List<EvalTimeseriesResponse>> getTimeseries(
            @RequestParam(defaultValue = "7d")  String range,
            @RequestParam(defaultValue = "day") String interval
    ) {
        String fmt = switch (interval) {
            case "hour" -> "%Y-%m-%d %H:00";
            case "day"  -> "%Y-%m-%d";
            default -> throw new CustomException(EvalErrorCode.INVALID_INTERVAL);
        };
        List<EvalTimeseriesResponse> result = repository.findTimeseries(rangeToFrom(range), fmt)
                .stream()
                .map(row -> new EvalTimeseriesResponse(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        nullableDouble(row[2]),
                        nullableDouble(row[3]),
                        nullableDouble(row[4]),
                        nullableDouble(row[5]),
                        nullableDouble(row[6])
                ))
                .toList();
        return SuccessResponse.ok(result);
    }

    /**
     * GET /internal/evals/similarity-histogram?range=7d
     * 전체 retrieved doc의 similarity score 구간별 분포 (0.1 단위 버킷)
     */
    @GetMapping("/similarity-histogram")
    public SuccessResponse<List<EvalSimilarityHistogramResponse>> getSimilarityHistogram(
            @RequestParam(defaultValue = "7d") String range
    ) {
        List<EvalSimilarityHistogramResponse> result = repository.findSimilarityHistogram(rangeToFrom(range))
                .stream()
                .map(row -> new EvalSimilarityHistogramResponse(
                        ((Number) row[0]).doubleValue(),
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).longValue()
                ))
                .toList();
        return SuccessResponse.ok(result);
    }

    private LocalDateTime rangeToFrom(String range) {
        return switch (range) {
            case "24h" -> LocalDateTime.now().minusHours(24);
            case "30d" -> LocalDateTime.now().minusDays(30);
            default    -> LocalDateTime.now().minusDays(7);
        };
    }

    private Double nullableDouble(Object o) {
        return o != null ? ((Number) o).doubleValue() : null;
    }
}
