package pingpong.backend.domain.eval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import pingpong.backend.domain.eval.dto.JudgeResult;

@Slf4j
@Service
public class LlmJudgeService {

    private final ChatClient judgeClient;
    private final ObjectMapper objectMapper;

    public LlmJudgeService(@Qualifier("judgeClient") ChatClient judgeClient,
                           ObjectMapper objectMapper) {
        this.judgeClient = judgeClient;
        this.objectMapper = objectMapper;
    }

    // ── System Prompt (역할 + 지표 정의 + 출력 규칙) ─────────────────────────────
    private static final String JUDGE_SYSTEM_PROMPT = """
            당신은 RAG 기반 AI 응답의 품질을 정밀하게 평가하는 Judge 시스템입니다.

            ## 역할
            제공된 [질문], [검색된 컨텍스트], [AI 답변]을 분석하여 아래 7개 지표를 채점합니다.
            모든 판단은 반드시 제공된 컨텍스트에 근거해야 하며, 외부 지식으로 보완하지 않습니다.

            ## 평가 지표 정의 및 채점 기준

            ### 수치 지표 (score: 0.0 ~ 1.0)

            **faithfulness** — 컨텍스트 충실도
            답변의 각 주장(claim)이 검색된 컨텍스트에 의해 직접 지지되는 비율.
            - 1.0: 모든 claim이 컨텍스트에서 명시적으로 도출됨
            - 0.7: 대부분의 claim이 컨텍스트에 있으나 일부 추론 포함
            - 0.4: 절반 이상의 claim이 컨텍스트 외 내용
            - 0.0: 컨텍스트와 무관하거나 완전히 새로운 내용

            **answer_relevance** — 질문 적합도
            답변이 사용자 질문의 의도를 얼마나 직접적으로 충족하는가.
            - 1.0: 질문의 모든 측면을 명확하고 완전하게 답변
            - 0.7: 질문에 답하나 불필요한 내용 포함 또는 일부 누락
            - 0.4: 질문과 부분적으로만 관련
            - 0.0: 질문과 무관한 답변

            **instruction** — 시스템 지시 준수도
            시스템 프롬프트 규칙(한국어 답변, 컨텍스트 외 정보 미사용, 날짜·상태 원문 인용 등)을 준수하는 정도.
            - 1.0: 모든 지시사항 완벽 준수
            - 0.7: 대부분 준수, 경미한 위반
            - 0.4: 일부 지시사항 위반
            - 0.0: 지시사항 전면 무시

            **hallucination** — 환각 비율
            답변에서 컨텍스트에 없거나 사실과 다른 정보의 비율.
            - 0.0: 환각 없음, 모든 정보가 컨텍스트에 근거
            - 0.2: 소량의 배경 지식/추론 포함 (경미)
            - 0.6: 상당 부분이 컨텍스트 외 정보
            - 1.0: 대부분 환각 정보

            **context_recall** — 컨텍스트 재현율
            질문 답변에 필요한 정보 중 검색된 컨텍스트 청크가 포함하는 비율.
            - 1.0: 필요 정보 전부 컨텍스트에 존재
            - 0.7: 대부분 필요 정보 포함
            - 0.4: 일부 필요 정보만 포함
            - 0.0: 필요 정보 전무

            **context_precision** — 컨텍스트 정밀도
            검색된 topK 청크 중 실제로 답변에 기여하는 유용한 청크의 비율.
            - 1.0: 모든 청크가 답변에 직접 기여
            - 0.6: 절반 이상이 유용
            - 0.2: 극소수만 유용
            - 0.0: 모든 청크가 무관

            ### 이진 지표 (flag: true / false)

            **contradiction** — 컨텍스트와의 모순 여부
            답변이 컨텍스트의 내용과 직접 상충되는 주장을 포함하는지.
            - false: 모순 없음 (컨텍스트와 일관됨)
            - true: 명백한 모순 존재 (예: 컨텍스트 "완료" → 답변 "미완료")

            ## 출력 규칙
            1. **JSON만 반환**하세요. 앞뒤 설명, 마크다운 코드 블록(```), 추가 텍스트 일절 금지.
            2. 각 reason은 **30자 이내 한국어**로 작성하세요.
            3. score 값은 소수점 두 자리 이하로 표현하세요 (예: 0.85).
            4. 판단 근거가 불충분하면 보수적으로(낮게) 채점하세요.
            """;

    // ── User Prompt Template ───────────────────────────────────────────────────
    private static final String JUDGE_USER_TEMPLATE = """
            [질문]
            {question}

            [검색된 컨텍스트]
            {context}

            [AI 답변]
            {answer}

            위 정보를 바탕으로 아래 JSON 형식으로만 응답하세요:
            {
              "faithfulness":      {"score": 0.0~1.0, "reason": "30자 이내"},
              "answer_relevance":  {"score": 0.0~1.0, "reason": "30자 이내"},
              "instruction":       {"score": 0.0~1.0, "reason": "30자 이내"},
              "hallucination":     {"score": 0.0~1.0, "reason": "30자 이내"},
              "contradiction":     {"flag": true/false, "reason": "30자 이내"},
              "context_recall":    {"score": 0.0~1.0, "reason": "30자 이내"},
              "context_precision": {"score": 0.0~1.0, "reason": "30자 이내"}
            }
            """;

    // ── Repair Prompt ──────────────────────────────────────────────────────────
    private static final String REPAIR_TEMPLATE = """
            아래 JSON이 손상되었습니다. 올바른 JSON으로만 수정해서 반환하세요.
            다른 텍스트는 절대 포함하지 마세요.

            손상된 JSON:
            {raw}

            올바른 JSON:
            """;

    /**
     * Judge 실행 결과를 (JudgeResult, rawResponse) 쌍으로 반환.
     * rawResponse는 항상 보존 (null이면 Judge 호출 자체 실패).
     * JudgeResult는 파싱 성공 시 non-null, 실패 시 null.
     */
    public JudgeOutcome judge(String question, String answer, String contextSummary) {
        String userPrompt = buildUserPrompt(question, answer, contextSummary);

        String raw;
        try {
            raw = judgeClient.prompt()
                    .system(JUDGE_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("JUDGE: LLM 호출 실패", e);
            return new JudgeOutcome(null, null);
        }

        // Step 1: Primary parse
        JudgeResult result = tryParse(raw);
        if (result != null) {
            log.info("JUDGE: Step1 파싱 성공");
            return new JudgeOutcome(result, raw);
        }

        // Step 2: Repair prompt
        log.warn("JUDGE: Step1 파싱 실패, repair 시도 — rawLength={}", raw != null ? raw.length() : 0);
        String repairedRaw = repairJson(raw);
        result = tryParse(repairedRaw);
        if (result != null) {
            log.info("JUDGE: Step2 Repair 파싱 성공");
            return new JudgeOutcome(result, repairedRaw);
        }

        // Step 3: 최종 실패 → raw 보존, result null → PARTIAL 처리
        log.error("JUDGE: Step2 Repair 후에도 파싱 실패 — raw='{}'", raw);
        return new JudgeOutcome(null, raw);
    }

    private JudgeResult tryParse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String json = extractJson(raw);
        try {
            return objectMapper.readValue(json, JudgeResult.class);
        } catch (Exception e) {
            log.debug("JUDGE: tryParse 실패 — {}", e.getMessage());
            return null;
        }
    }

    private String repairJson(String raw) {
        if (raw == null) return "{}";
        try {
            return judgeClient.prompt()
                    .user(REPAIR_TEMPLATE.replace("{raw}", raw))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("JUDGE: Repair 호출 자체 실패", e);
            return "{}";
        }
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end   = raw.lastIndexOf('}');
        return (start >= 0 && end > start) ? raw.substring(start, end + 1) : raw.trim();
    }

    private String buildUserPrompt(String question, String answer, String context) {
        return JUDGE_USER_TEMPLATE
                .replace("{question}", question)
                .replace("{answer}", answer)
                .replace("{context}", context);
    }

    /**
     * Judge 실행 결과를 담는 내부 DTO.
     * result가 null이면 파싱 실패 (PARTIAL), raw도 null이면 LLM 호출 자체 실패 (FAILED).
     */
    public record JudgeOutcome(JudgeResult result, String raw) {}
}
