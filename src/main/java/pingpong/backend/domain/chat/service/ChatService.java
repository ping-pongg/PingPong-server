package pingpong.backend.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import pingpong.backend.domain.chat.ChatErrorCode;
import pingpong.backend.domain.chat.dto.ChatRequest;
import pingpong.backend.domain.chat.dto.ChatResponse;
import pingpong.backend.global.exception.CustomException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatResponse ask(Long teamId, ChatRequest request) {
        String filterExpression = "teamId == " + teamId;

        log.info("CHAT: ask() called — teamId={} messageLength={}", teamId, request.message().length());
        log.info("CHAT: RAG filter expression='{}'", filterExpression);

        // [진단] QuestionAnswerAdvisor가 실제로 사용할 VectorStore 검색 결과를 미리 확인
        diagnosVectorStoreContext(teamId, request.message(), filterExpression);

        try {
            log.info("CHAT: calling ChatClient (QuestionAnswerAdvisor + OpenAI) — teamId={}", teamId);
            String answer = chatClient.prompt()
                    .user(request.message())
                    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filterExpression))
                    .call()
                    .content();

            log.info("CHAT: AI response received — teamId={} answerLength={}",
                    teamId, answer != null ? answer.length() : 0);
            return new ChatResponse(answer);
        } catch (Exception e) {
            log.error("CHAT: AI call FAILED — teamId={} filterExpression='{}' errorType={} message='{}'",
                    teamId, filterExpression, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new CustomException(ChatErrorCode.CHAT_AI_CALL_FAILED);
        }
    }

    /**
     * [RAG 진단] QuestionAnswerAdvisor 내부에서 수행될 VectorStore 검색을 동일 조건으로 미리 실행하여
     * 컨텍스트 주입 가능 여부를 로그로 확인합니다.
     * 컨텍스트가 0건이면 LLM에 RAG 정보가 주입되지 않아 할루시네이션이 발생할 수 있습니다.
     */
    private void diagnosVectorStoreContext(Long teamId, String message, String filterExpression) {
        // ── Step 1: 실제 필터 + threshold=0.5 로 검색 (QuestionAnswerAdvisor와 동일 조건)
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(message)
                            .topK(5)
                            .similarityThreshold(0.5)
                            .filterExpression(filterExpression)
                            .build()
            );

            if (docs == null || docs.isEmpty()) {
                log.warn("CHAT-RAG [Step1]: 필터+threshold=0.5 → 결과 0건. teamId={} filter='{}'",
                        teamId, filterExpression);

                // ── Step 2: threshold=0.0 으로 낮춰서 재검색 (유사도 임계값 문제 여부 확인)
                try {
                    List<Document> lowThresholdDocs = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query(message)
                                    .topK(5)
                                    .similarityThreshold(0.0)
                                    .filterExpression(filterExpression)
                                    .build()
                    );
                    if (lowThresholdDocs == null || lowThresholdDocs.isEmpty()) {
                        log.warn("CHAT-RAG [Step2]: 필터+threshold=0.0 → 결과 0건. → 원인: ①teamId={} 에 해당하는 데이터가 Pinecone에 없음 또는 ②필터 표현식 타입 불일치",
                                teamId);

                        // ── Step 3: 필터 없이 전체 검색 (Pinecone에 데이터 자체가 있는지 확인)
                        try {
                            List<Document> noFilterDocs = vectorStore.similaritySearch(
                                    SearchRequest.builder()
                                            .query(message)
                                            .topK(3)
                                            .similarityThreshold(0.0)
                                            .build()
                            );
                            if (noFilterDocs == null || noFilterDocs.isEmpty()) {
                                log.warn("CHAT-RAG [Step3]: 필터 없음+threshold=0.0 → 결과 0건. → 원인 확정: Pinecone 인덱스가 비어있음 (인덱싱이 실행되지 않았거나 실패). indexing_state 테이블 확인 필요");
                            } else {
                                log.warn("CHAT-RAG [Step3]: 필터 없음+threshold=0.0 → 결과 {}건 존재! → 원인 확정: 필터 조건 문제 (teamId 메타데이터 타입 불일치 가능성)",
                                        noFilterDocs.size());
                                Object teamIdValue = noFilterDocs.get(0).getMetadata().get("teamId");
                                log.warn("CHAT-RAG [Step3]: Pinecone 내 teamId 메타데이터 값={} 타입={} (필터식: 'teamId == {}' — Long 숫자로 저장되어 있는지 확인)",
                                        teamIdValue,
                                        teamIdValue != null ? teamIdValue.getClass().getSimpleName() : "null",
                                        teamId);
                            }
                        } catch (Exception e3) {
                            log.error("CHAT-RAG [Step3]: 필터 없는 쿼리 실패 — errorType={} message='{}'",
                                    e3.getClass().getSimpleName(), e3.getMessage(), e3);
                        }

                    } else {
                        log.warn("CHAT-RAG [Step2]: 필터+threshold=0.0 → 결과 {}건 존재! → 원인 확정: similarityThreshold=0.5 가 너무 높음 (실제 최고 유사도가 0.5 미만). threshold 낮추는 것 검토 필요",
                                lowThresholdDocs.size());
                        log.warn("CHAT-RAG [Step2]: 가장 유사한 문서 score={} id={}",
                                lowThresholdDocs.get(0).getScore(), lowThresholdDocs.get(0).getId());
                    }
                } catch (Exception e2) {
                    log.error("CHAT-RAG [Step2]: threshold=0.0 쿼리 실패 — errorType={} message='{}'",
                            e2.getClass().getSimpleName(), e2.getMessage(), e2);
                }

            } else {
                log.info("CHAT-RAG [Step1]: 결과 {}건 — LLM 프롬프트에 컨텍스트 주입 예정. teamId={} filter='{}'",
                        docs.size(), teamId, filterExpression);
                for (int i = 0; i < docs.size(); i++) {
                    Document doc = docs.get(i);
                    log.debug("CHAT-RAG: context[{}] id={} score={} sourceKey={} contentLength={}",
                            i, doc.getId(), doc.getScore(),
                            doc.getMetadata().get("sourceKey"),
                            doc.getText() != null ? doc.getText().length() : 0);
                }
            }
        } catch (Exception e) {
            log.error("CHAT-RAG [Step1]: VectorStore 진단 쿼리 실패 — teamId={} filter='{}' errorType={} message='{}'",
                    teamId, filterExpression, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}
