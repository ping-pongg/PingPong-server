package pingpong.backend.global.rag.indexing.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import pingpong.backend.global.rag.indexing.enums.IndexSourceType;
import pingpong.backend.global.rag.indexing.dto.IndexJob;
import pingpong.backend.global.rag.indexing.normalizer.IndexingNormalizer;
import pingpong.backend.global.rag.indexing.repository.VectorStoreGateway;
import pingpong.backend.global.rag.indexing.text.Chunker;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class IndexJobWorker implements IndexJobHandler {

    private final List<IndexingNormalizer> normalizers;
    private final Chunker chunker;
    private final VectorStoreGateway pineconeVectorStoreGateway;

    @Async("indexExecutor")
    @Override
    public void handle(IndexJob job) {
        try {
            IndexingNormalizer normalizer = resolveNormalizer(job.sourceType());
            if (normalizer == null) {
                log.warn("VECTORIZE: no normalizer registered for sourceType={}", job.sourceType());
                return;
            }

            String normalizedText = normalizer.normalize(job);
            if (normalizedText == null || normalizedText.isBlank()) {
                return;
            }

            List<String> chunks = chunker.chunk(normalizedText);
            if (chunks.isEmpty()) {
                return;
            }

            pineconeVectorStoreGateway.upsert(job, chunks, normalizedText);
        } catch (Exception e) {
            log.error("VECTORIZE: failed to process sourceType={} teamId={} apiPath={} resourceId={}",
                    job.sourceType(), job.teamId(), job.apiPath(), job.resourceId(), e);
        }
    }

    private IndexingNormalizer resolveNormalizer(IndexSourceType sourceType) {
        for (IndexingNormalizer normalizer : normalizers) {
            if (normalizer.supports(sourceType)) {
                return normalizer;
            }
        }
        return null;
    }
}
