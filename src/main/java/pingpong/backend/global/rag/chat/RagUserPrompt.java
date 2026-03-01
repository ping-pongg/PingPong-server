package pingpong.backend.global.rag.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import pingpong.backend.global.rag.chat.config.RagChatProperties;

import java.util.List;

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

    public String build(String query, List<Document> docs) {
        String context = buildContext(docs);
        return RAG_PROMPT_TEMPLATE
                .replace("{question_answer_context}", context)
                .replace("{query}", query != null ? query : "");
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
