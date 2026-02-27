package pingpong.backend.global.rag.chat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "rag.chat")
public class RagChatProperties {

    private int topK = 5;

    private double similarityThreshold = 0.1;

    private int maxDocChars = 1_500;

    private int maxContextChars = 12_000;
}
