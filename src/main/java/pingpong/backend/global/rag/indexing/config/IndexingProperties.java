package pingpong.backend.global.rag.indexing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notion.indexing")
public class IndexingProperties {

    private boolean enabled = true;

    private int chunkSize = 1200;

    private int chunkOverlap = 200;

    private int maxNormalizedChars = 120000;

    private int executorCorePoolSize = 2;

    private int executorMaxPoolSize = 4;

    private int executorQueueCapacity = 300;
}
