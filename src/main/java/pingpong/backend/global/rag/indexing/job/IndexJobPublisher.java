package pingpong.backend.global.rag.indexing.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pingpong.backend.global.rag.indexing.dto.IndexJob;

@Component
@RequiredArgsConstructor
public class IndexJobPublisher {

    private final IndexJobHandler indexJobHandler;

    public void publish(IndexJob job) {
        indexJobHandler.handle(job);
    }
}
