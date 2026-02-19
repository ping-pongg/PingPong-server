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
                log.warn("CHAT-RAG: VectorStore 검색 결과 0건 — LLM 프롬프트에 컨텍스트가 주입되지 않습니다. " +
                                "teamId={} filter='{}' (가능한 원인: ①해당 teamId로 인덱싱된 데이터 없음 ②필터 타입 불일치 ③similarityThreshold=0.5 초과)",
                        teamId, filterExpression);
            } else {
                log.info("CHAT-RAG: VectorStore 검색 결과 {}건 — LLM 프롬프트에 컨텍스트 주입 예정. teamId={} filter='{}'",
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
            log.error("CHAT-RAG: VectorStore 진단 쿼리 실패 — teamId={} filter='{}' errorType={} message='{}'",
                    teamId, filterExpression, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}
