package pingpong.backend.domain.eval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pingpong.backend.domain.eval.dto.judge.JudgeResult;
import pingpong.backend.domain.eval.enums.EvalStatus;
import pingpong.backend.domain.eval.LlmEvalCase;
import pingpong.backend.domain.eval.repository.LlmEvalCaseRepository;
import pingpong.backend.domain.eval.service.LlmJudgeService.JudgeOutcome;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmEvalService {

    private final LlmEvalCaseRepository repository;
    private final LlmJudgeService judgeService;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelName;

    private static final double COST_REGULAR_INPUT = 0.40 / 1_000_000.0;  // $0.40/1M
    private static final double COST_CACHED_INPUT  = 0.10 / 1_000_000.0;  // $0.10/1M (75% 할인)
    private static final double COST_OUTPUT        = 1.60 / 1_000_000.0;  // $1.60/1M

    /**
     * 평가 실행 및 저장.
     * ChatService에서 응답 반환 후 비동기(LlmEvalAsyncService)로 호출됨.
     */
    public void evaluateAndSave(
            Long teamId,
            String question,
            String answer,
            List<Document> retrievedDocs,
            ChatResponse chatResponse,
            int latencyTotal,
            int latencyRetrieval,
            int latencyGeneration
    ) {
        String requestId = UUID.randomUUID().toString();
        long evalStart = System.currentTimeMillis();

        LlmEvalCase.LlmEvalCaseBuilder builder = LlmEvalCase.builder()
                .requestId(requestId)
                .teamId(teamId)
                .questionText(question)
                .answerText(answer)
                .latencyMsTotal(latencyTotal)
                .latencyMsRetrieval(latencyRetrieval)
                .latencyMsGeneration(latencyGeneration)
                .modelName(modelName);

        // ── 토큰 및 비용 수집 ─────────────────────────────────────────────────────
        // Usage(nativeUsage)는 Spring AI/OpenAI 구현에 따라 Map이거나 POJO일 수 있어 Map으로 변환 후 읽는다.
        try {
            Object nativeUsage = chatResponse.getMetadata().getUsage().getNativeUsage();

            log.debug("EVAL: usage wrapper — requestId={} promptTokens={} completionTokens={} totalTokens={} nativeType={}",
                    requestId,
                    chatResponse.getMetadata().getUsage().getPromptTokens(),
                    chatResponse.getMetadata().getUsage().getCompletionTokens(),
                    chatResponse.getMetadata().getUsage().getTotalTokens(),
                    nativeUsage != null ? nativeUsage.getClass().getName() : "null");

            Map<String, Object> nativeMap = coerceToMap(nativeUsage);
            if (nativeMap == null) {
                log.warn("EVAL: 토큰 수집 실패 — nativeUsage Map 변환 실패 requestId={} nativeType={}",
                        requestId, nativeUsage != null ? nativeUsage.getClass().getName() : "null");
            } else {
                log.debug("EVAL: nativeMap 키 목록 — requestId={} keys={}", requestId, nativeMap.keySet());

                int tokensIn = firstNonZeroInt(nativeMap, "prompt_tokens", "promptTokens");
                int tokensOut = firstNonZeroInt(nativeMap, "completion_tokens", "completionTokens");
                int tokensTotal = firstNonZeroInt(nativeMap, "total_tokens", "totalTokens");

                // Fallback
                if (tokensIn == 0 && tokensOut == 0 && tokensTotal == 0) {
                    tokensIn = chatResponse.getMetadata().getUsage().getPromptTokens();
                    tokensOut = chatResponse.getMetadata().getUsage().getCompletionTokens();
                    tokensTotal = chatResponse.getMetadata().getUsage().getTotalTokens();
                    if (tokensIn == 0 && tokensOut == 0) {
                        log.warn("EVAL: nativeMap + wrapper 모두 토큰 0 — requestId={} streaming 사용 시 streamUsage(true) 설정 여부 확인 필요",
                                requestId);
                    }
                }

                int tokensCached = extractCachedTokens(chatResponse);
                int regularInput = tokensIn - tokensCached;
                double costUsd   = regularInput * COST_REGULAR_INPUT
                                 + tokensCached  * COST_CACHED_INPUT
                                 + tokensOut     * COST_OUTPUT;

                builder.tokensIn(tokensIn)
                       .tokensOut(tokensOut)
                       .tokensCached(tokensCached > 0 ? tokensCached : null)
                       .tokensTotal(tokensTotal)
                       .costUsd(costUsd);

                log.info("EVAL: 토큰 수집 완료 — requestId={} in={} out={} cached={} costUsd={}",
                        requestId, tokensIn, tokensOut, tokensCached,
                        String.format("%.6f", costUsd));
            }
        } catch (Exception e) {
            log.warn("EVAL: 토큰 수집 실패 — requestId={} errorType={} message='{}'",
                    requestId, e.getClass().getSimpleName(), e.getMessage());
        }

        // ── context JSON ─────────────────────────────────────────────────────────
        builder.contextJson(buildContextJson(retrievedDocs));

        // ── Similarity stats ──────────────────────────────────────────────────
        if (retrievedDocs != null && !retrievedDocs.isEmpty()) {
            List<Double> scores = retrievedDocs.stream()
                    .map(Document::getScore)
                    .filter(Objects::nonNull)
                    .toList();
            builder.retrievedDocCount(retrievedDocs.size());
            if (!scores.isEmpty()) {
                builder.avgSimilarityScore(scores.stream().mapToDouble(d -> d).average().orElseThrow())
                       .minSimilarityScore(scores.stream().mapToDouble(d -> d).min().orElseThrow())
                       .maxSimilarityScore(scores.stream().mapToDouble(d -> d).max().orElseThrow());
            }
        } else {
            builder.retrievedDocCount(0);
        }

        // ── Judge 호출 (단일 호출로 raw + result 동시 획득) ───────────────────────
        String contextSummary = buildContextSummary(retrievedDocs);
        JudgeOutcome outcome = judgeService.judge(question, answer, contextSummary);

        if (outcome.raw() == null) {
            // LLM 호출 자체 실패 → FAILED
            builder.evalStatus(EvalStatus.FAILED)
                   .evalError("Judge LLM 호출 실패");
        } else if (outcome.result() != null) {
            // 파싱 성공 → DONE
            applyJudgeResult(builder, outcome.result());
            builder.judgeReasonJson(serializeSafe(outcome.result()))
                   .evalStatus(EvalStatus.DONE);
        } else {
            // 파싱 실패 but raw 존재 → PARTIAL (raw 보존)
            builder.judgeReasonJson(outcome.raw())
                   .evalStatus(EvalStatus.PARTIAL)
                   .evalError("JSON 파싱 실패 — raw 응답은 judge_reason_json에 보존됨");
            log.warn("EVAL: PARTIAL 저장 — requestId={}", requestId);
        }

        builder.latencyMsEval((int) (System.currentTimeMillis() - evalStart));

        LlmEvalCase saved = repository.save(builder.build());
        log.info("EVAL: 저장 완료 — id={} requestId={} status={} teamId={}",
                saved.getId(), requestId, saved.getEvalStatus(), teamId);
    }

    private void applyJudgeResult(LlmEvalCase.LlmEvalCaseBuilder builder, JudgeResult j) {
        if (j.faithfulness()     != null) builder.faithfulnessScore(j.faithfulness().score());
        if (j.answerRelevance()  != null) builder.answerRelevanceScore(j.answerRelevance().score());
        if (j.instruction()      != null) builder.instructionScore(j.instruction().score());
        if (j.hallucination()    != null) builder.hallucinationRate(j.hallucination().score());
        if (j.contradiction()    != null) builder.contradictionFlag(Boolean.TRUE.equals(j.contradiction().flag()));
        if (j.contextRecall()    != null) builder.contextRecall(j.contextRecall().score());
        if (j.contextPrecision() != null) builder.contextPrecision(j.contextPrecision().score());
    }

    /**
     * OpenAI Prompt Cache 재사용 토큰 수 추출.
     * Spring AI 1.1.2: getNativeUsage()가 Map<String, Object> 반환
     */
    private int extractCachedTokens(ChatResponse chatResponse) {
        try {
            Object native_ = chatResponse.getMetadata().getUsage().getNativeUsage();
            Map<String, Object> nativeMap = coerceToMap(native_);
            if (nativeMap != null) {
                Object details = nativeMap.getOrDefault("prompt_tokens_details", nativeMap.get("promptTokensDetails"));
                Map<String, Object> detailsMap = coerceToMap(details);
                if (detailsMap != null) {
                    return firstNonZeroInt(detailsMap, "cached_tokens", "cachedTokens");
                }
            }
        } catch (Exception e) {
            log.debug("EVAL: Cache 토큰 추출 실패 — 0으로 처리: {}", e.getMessage());
        }
        return 0;
    }

    private int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private int firstNonZeroInt(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            int i = toInt(map.get(k));
            if (i != 0) return i;
        }
        return 0;
    }

    private Map<String, Object> coerceToMap(Object value) {
        if (value == null) return null;

        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() == null) continue;
                out.put(String.valueOf(e.getKey()), (Object) e.getValue());
            }
            return out;
        }

        // Jackson convertValue handles POJOs/records (e.g., native Usage objects) into a map.
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> converted = objectMapper.convertValue(value, Map.class);
            return converted;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildContextJson(List<Document> docs) {
        if (docs == null || docs.isEmpty()) return "[]";
        try {
            var list = docs.stream().map(d -> Map.of(
                    "id",        d.getId() != null ? d.getId() : "",
                    "score",     d.getScore() != null ? d.getScore() : 0.0,
                    "sourceKey", d.getMetadata().getOrDefault("sourceKey", ""),
                    "preview",   d.getText() != null && d.getText().length() > 300
                                 ? d.getText().substring(0, 300) : (d.getText() != null ? d.getText() : "")
            )).toList();
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String buildContextSummary(List<Document> docs) {
        if (docs == null || docs.isEmpty()) return "(컨텍스트 없음)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            sb.append("[청크 ").append(i + 1).append("]\n");
            String text = docs.get(i).getText();
            if (text != null) {
                sb.append(text.length() > 500 ? text.substring(0, 500) : text);
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private String serializeSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
