package pingpong.backend.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import pingpong.backend.domain.chat.ChatErrorCode;
import pingpong.backend.domain.chat.dto.ChatRequest;
import pingpong.backend.domain.chat.dto.ChatResponse;
import pingpong.backend.domain.eval.service.LlmEvalAsyncService;
import pingpong.backend.global.exception.CustomException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final LlmEvalAsyncService evalAsyncService;

    @Value("${rag.chat.top-k}")
    private int topK;

    @Value("${rag.chat.similarity-threshold}")
    private double similarityThreshold;

    public ChatResponse ask(Long teamId, ChatRequest request) {
        long totalStart = System.currentTimeMillis();
        String filterExpression = "teamId == " + teamId;

        log.info("CHAT: ask() — teamId={} messageLength={}", teamId, request.message().length());

        // 1. Retrieval: VectorStore 검색 (context 캡처 + latency 측정)
        long retrievalStart = System.currentTimeMillis();
        List<Document> retrievedDocs = retrieveContext(teamId, request.message(), filterExpression);
        int latencyRetrieval = (int) (System.currentTimeMillis() - retrievalStart);

        // 2. Generation: chatResponse()로 변경해 토큰 메타데이터 보존
        long generationStart = System.currentTimeMillis();
        org.springframework.ai.chat.model.ChatResponse chatResponse;
        String answer;
        try {
            log.info("CHAT: calling ChatClient — teamId={}", teamId);
            chatResponse = chatClient.prompt()
                    .user(request.message())
                    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filterExpression))
                    .call()
                    .chatResponse();
            answer = chatResponse.getResult().getOutput().getText();
            log.info("CHAT: AI 응답 수신 — teamId={} answerLength={}", teamId,
                    answer != null ? answer.length() : 0);
        } catch (Exception e) {
            log.error("CHAT: AI 호출 실패 — teamId={} errorType={} message='{}'",
                    teamId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new CustomException(ChatErrorCode.CHAT_AI_CALL_FAILED);
        }
        int latencyGeneration = (int) (System.currentTimeMillis() - generationStart);
        int latencyTotal      = (int) (System.currentTimeMillis() - totalStart);

        // 3. 비동기 평가 (사용자 응답 반환 후 처리 → 사용자 latency 무영향)
        evalAsyncService.evaluateAndSave(
                teamId, request.message(), answer,
                retrievedDocs, chatResponse,
                latencyTotal, latencyRetrieval, latencyGeneration
        );

        return new ChatResponse(answer);
    }

    /**
     * VectorStore 유사도 검색.
     * 실패 시 빈 리스트 반환 (평가 저장은 context 없이 계속 진행).
     */
    private List<Document> retrieveContext(Long teamId, String message, String filterExpression) {
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(message)
                            .topK(topK)
                            .similarityThreshold(similarityThreshold)
                            .filterExpression(filterExpression)
                            .build()
            );
            int count = (docs != null) ? docs.size() : 0;
            log.info("CHAT-RAG: 컨텍스트 {}건 검색됨 — teamId={} filter='{}'", count, teamId, filterExpression);

            if (count == 0) {
                log.warn("CHAT-RAG: 결과 0건 — teamId={} (인덱싱 상태 및 필터 표현식 확인 필요)", teamId);
            }
            return docs != null ? docs : List.of();
        } catch (Exception e) {
            log.error("CHAT-RAG: VectorStore 검색 실패 — teamId={} errorType={} message='{}'",
                    teamId, e.getClass().getSimpleName(), e.getMessage(), e);
            return List.of();
        }
    }
}
