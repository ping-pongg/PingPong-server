package pingpong.backend.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
            당신은 팀의 노션 문서를 기반으로 질문에 답변하는 AI 어시스턴트입니다.
            제공된 컨텍스트 정보를 바탕으로 정확하고 도움이 되는 답변을 한국어로 제공하세요.
            컨텍스트에 관련 정보가 없으면 솔직하게 모른다고 답변하세요.
            """;

    private static final String RAG_PROMPT_TEMPLATE = """
            아래의 컨텍스트 정보를 활용하여 사용자의 질문에 답변하세요.

            ---------------------
            컨텍스트:
            {question_answer_context}
            ---------------------

            질문: {query}
            답변:
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().topK(5).similarityThreshold(0.5).build())
                .promptTemplate(new PromptTemplate(RAG_PROMPT_TEMPLATE))
                .build();

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(qaAdvisor)
                .build();
    }
}
