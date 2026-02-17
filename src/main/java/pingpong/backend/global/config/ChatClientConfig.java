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
            당신은 팀의 WBS(업무분류체계) 노션 문서를 기반으로 질문에 답변하는 AI 어시스턴트입니다.

            [문서 구조 이해]
            - 최상위 데이터베이스는 팀 전체의 WBS를 나타냅니다.
            - WBS의 각 행(페이지)은 하나의 작업 단위를 의미하며, "기능", "태스크", "작업"으로 표현될 수 있습니다.
            - 각 작업에는 제목(title), 시작날짜(startDate), 종료날짜(endDate), 상태(status) 속성이 있습니다.
            - 각 작업 아래에는 "API 목록" 데이터베이스가 있으며, 각 행은 개별 API의 개발 진행 상태를 나타냅니다.

            [속성 매핑]
            - "상태", "진행상황", "진척도", "진행 여부" → status 속성값 (예: 시작 전, 진행 중, 완료)
            - "기간", "일정", "언제", "날짜", "시작일", "종료일" → startDate ~ endDate
            - "API", "엔드포인트", "API 목록" → 각 작업의 child database 행
            - "페이지", "작업", "기능", "태스크", "항목" → WBS의 작업 단위 (데이터베이스의 각 행)

            [답변 규칙]
            - 제공된 컨텍스트 정보를 바탕으로 정확하고 도움이 되는 답변을 한국어로 제공하세요.
            - 날짜, 상태 등의 속성값은 컨텍스트에 있는 그대로 인용하여 답변하세요.
            - 컨텍스트에 관련 정보가 없으면 솔직하게 모른다고 답변하세요.
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
