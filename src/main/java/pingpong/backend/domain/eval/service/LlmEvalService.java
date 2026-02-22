package pingpong.backend.domain.eval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import pingpong.backend.domain.eval.dto.JudgeResult;
import pingpong.backend.domain.eval.enums.EvalStatus;
import pingpong.backend.domain.eval.LlmEvalCase;
import pingpong.backend.domain.eval.repository.LlmEvalCaseRepository;
import pingpong.backend.domain.eval.service.LlmJudgeService.JudgeOutcome;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmEvalService {

    private final LlmEvalCaseRepository repository;
    private final LlmJudgeService judgeService;
    private final ObjectMapper objectMapper;

    // gpt-4.1-mini 단가 (변경 시 여기만 수정)
    private static final double COST_REGULAR_INPUT = 0.40 / 1_000_000.0;  // $0.40/1M
    private static final double COST_CACHED_INPUT  = 0.10 / 1_000_000.0;  // $0.10/1M (75% 할인)
    private static final double COST_OUTPUT        = 1.60 / 1_000_000.0;  // $1.60/1M
    private static final String MODEL_NAME         = "gpt-4.1-mini";

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
                .modelName(MODEL_NAME);

        // ── 토큰 및 비용 수집 (Cache 토큰 포함) ──────────────────────────────────
        try {
            var usage = chatResponse.getMetadata().getUsage();
            // Spring AI 1.1.2: getPromptTokens() / getCompletionTokens() 반환형은 Integer
            int tokensIn    = usage.getPromptTokens();
            int tokensOut   = usage.getCompletionTokens();
            int tokensTotal = usage.getTotalTokens();

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

        } catch (Exception e) {
            log.warn("EVAL: 토큰 수집 실패 — requestId={}", requestId, e);
        }

        // ── context JSON ─────────────────────────────────────────────────────────
        builder.contextJson(buildContextJson(retrievedDocs));

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
     * OpenAI Prompt Cache 토큰 수 추출.
     * Spring AI 1.1.2 기준 — OpenAiUsage 캐스팅으로 시도, 실패 시 0 반환 (비용 과대 계상).
     */
    /**
     * OpenAI Prompt Cache 재사용 토큰 수 추출.
     * Spring AI 1.1.2: OpenAiUsage 래퍼 클래스 없음.
     * getNativeUsage() → OpenAiApi.Usage → promptTokensDetails().cachedTokens() 경로로 접근.
     */
    private int extractCachedTokens(ChatResponse chatResponse) {
        try {
            Object native_ = chatResponse.getMetadata().getUsage().getNativeUsage();
            if (native_ instanceof OpenAiApi.Usage openAiUsage) {
                OpenAiApi.Usage.PromptTokensDetails details = openAiUsage.promptTokensDetails();
                if (details != null && details.cachedTokens() != null) {
                    return details.cachedTokens();
                }
            }
        } catch (Exception e) {
            log.debug("EVAL: Cache 토큰 추출 실패 — 0으로 처리: {}", e.getMessage());
        }
        return 0;
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
