package pingpong.backend.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.stereotype.Service;
import pingpong.backend.domain.chat.ChatErrorCode;
import pingpong.backend.domain.chat.dto.ChatRequest;
import pingpong.backend.domain.chat.dto.ChatResponse;
import pingpong.backend.global.exception.CustomException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;

    public ChatResponse ask(Long teamId, ChatRequest request) {
        String filterExpression = "teamId == " + teamId;

        try {
            String answer = chatClient.prompt()
                    .user(request.message())
                    .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filterExpression))
                    .call()
                    .content();

            return new ChatResponse(answer);
        } catch (Exception e) {
            log.error("AI 응답 생성 실패: teamId={}, message={}", teamId, request.message(), e);
            throw new CustomException(ChatErrorCode.CHAT_AI_CALL_FAILED);
        }
    }
}
