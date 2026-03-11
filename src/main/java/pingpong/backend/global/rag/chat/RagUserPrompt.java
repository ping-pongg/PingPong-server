package pingpong.backend.global.rag.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import pingpong.backend.domain.swagger.dto.EndpointAggregate;
import pingpong.backend.global.rag.chat.config.RagChatProperties;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RagUserPrompt {

    private static final String RAG_PROMPT_TEMPLATE = """
            아래의 컨텍스트 정보를 활용하여 사용자의 질문에 답변하세요.

            ---------------------
            컨텍스트:
            {question_answer_context}
            ---------------------

            질문: {query}
            답변:
            """;



    private final RagChatProperties properties;
    private final ObjectMapper objectMapper;

    public String build(String query, List<Document> docs) {
        String context = buildContext(docs);
        return RAG_PROMPT_TEMPLATE
                .replace("{question_answer_context}", context)
                .replace("{query}", query != null ? query : "");
    }

    /**
     * 유저 프롬프트 생성 (RAG 컨텍스트 결합)
     */
    public String buildUserPrompt(EndpointAggregate spec) {
        try {
            // Swagger 관련 집합 정보를 읽기 좋은 JSON으로 변환
            String swaggerJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(spec);

            return String.format("""
                제공된 API 명세를 분석하여 QA 시나리오를 생성하세요.
                결과 JSON의 'endpointId' 필드에는 반드시 아래의 ID를 사용해야 합니다.
                
                [대상 endpointId]
                %d
                
                [분석할 API 명세]
                %s
                """, spec.endpoint().getId(), swaggerJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Swagger 데이터 변환 중 오류가 발생했습니다.", e);
        }
    }


    private String buildContext(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return "(관련 컨텍스트 없음)";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);

            String sourceKey = "";
            String pageUrl = "";
            if (doc.getMetadata() != null) {
                Object v = doc.getMetadata().get("sourceKey");
                if (v != null) sourceKey = String.valueOf(v);
                Object u = doc.getMetadata().get("pageUrl");
                if (u != null) pageUrl = String.valueOf(u);
            }

            String text = doc.getText() != null ? doc.getText() : "";
            if (text.length() > properties.getMaxDocChars()) {
                text = text.substring(0, properties.getMaxDocChars()) + " ...";
            }

            sb.append("[doc ").append(i + 1).append("]");
            if (doc.getId() != null && !doc.getId().isBlank()) {
                sb.append(" id=").append(doc.getId());
            }
            if (doc.getScore() != null) {
                sb.append(" score=").append(doc.getScore());
            }
            if (!sourceKey.isBlank()) {
                sb.append(" sourceKey=").append(sourceKey);
            }
            if (!pageUrl.isBlank()) {
                sb.append(" pageUrl=").append(pageUrl);
            }
            sb.append("\n");
            sb.append(text).append("\n\n");

            if (sb.length() >= properties.getMaxContextChars()) {
                sb.append("(컨텍스트 일부 생략됨)\n");
                break;
            }
        }
        return sb.toString().trim();
    }
}
