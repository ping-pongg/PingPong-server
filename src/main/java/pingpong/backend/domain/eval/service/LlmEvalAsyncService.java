package pingpong.backend.domain.eval.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmEvalAsyncService {

    private final LlmEvalService evalService;

    /**
     * 비동기 평가 실행.
     * ChatService에서 사용자에게 응답을 반환한 직후 호출되므로, 사용자 latency에 영향을 주지 않음.
     */
    @Async("evalExecutor")
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
        try {
            evalService.evaluateAndSave(teamId, question, answer, retrievedDocs,
                    chatResponse, latencyTotal, latencyRetrieval, latencyGeneration);
        } catch (Exception e) {
            log.error("EVAL-ASYNC: 저장 실패 — teamId={}", teamId, e);
        }
    }
}
