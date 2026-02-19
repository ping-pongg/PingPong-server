package pingpong.backend.global.rag.indexing.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import pingpong.backend.global.exception.CustomException;
import pingpong.backend.global.rag.indexing.enums.IndexSourceType;
import pingpong.backend.global.rag.indexing.dto.IndexJob;
import pingpong.backend.global.rag.indexing.IndexingErrorCode;
import pingpong.backend.global.rag.indexing.normalizer.IndexingNormalizer;
import pingpong.backend.global.rag.indexing.repository.VectorStoreGateway;
import pingpong.backend.global.rag.indexing.text.Chunker;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class IndexJobWorker implements IndexJobHandler {

    private final Map<IndexSourceType, IndexingNormalizer> normalizerMap;
    private final Chunker chunker;
    private final VectorStoreGateway pineconeVectorStoreGateway;

    public IndexJobWorker(List<IndexingNormalizer> normalizers,
                          Chunker chunker,
                          VectorStoreGateway pineconeVectorStoreGateway) {
        this.normalizerMap = normalizers.stream()
                .collect(Collectors.toMap(IndexingNormalizer::sourceType, Function.identity()));
        this.chunker = chunker;
        this.pineconeVectorStoreGateway = pineconeVectorStoreGateway;
    }

    @Async("indexExecutor")
    @Override
    public void handle(IndexJob job) {
        log.info("VECTORIZE: job received — sourceType={} teamId={} apiPath={} resourceId={}",
                job.sourceType(), job.teamId(), job.apiPath(), job.resourceId());
        try {
            IndexingNormalizer normalizer = resolveNormalizer(job.sourceType());

            String normalizedText = normalizer.normalize(job);
            if (normalizedText == null || normalizedText.isBlank()) {
                log.warn("VECTORIZE: normalizedText is empty — skipping upsert (데이터 없거나 파싱 실패). sourceType={} teamId={} apiPath={} resourceId={}",
                        job.sourceType(), job.teamId(), job.apiPath(), job.resourceId());
                return;
            }
            log.info("VECTORIZE: normalized — textLength={} sourceType={} teamId={} apiPath={} resourceId={}",
                    normalizedText.length(), job.sourceType(), job.teamId(), job.apiPath(), job.resourceId());

            List<String> chunks = chunker.chunk(normalizedText);
            if (chunks.isEmpty()) {
                log.warn("VECTORIZE: chunk result is empty — skipping upsert (청크 분할 실패 또는 텍스트가 너무 짧음). sourceType={} teamId={} apiPath={} resourceId={} textLength={}",
                        job.sourceType(), job.teamId(), job.apiPath(), job.resourceId(), normalizedText.length());
                return;
            }
            log.info("VECTORIZE: chunked — chunkCount={} sourceType={} teamId={} apiPath={} resourceId={}",
                    chunks.size(), job.sourceType(), job.teamId(), job.apiPath(), job.resourceId());

            pineconeVectorStoreGateway.upsert(job, chunks, normalizedText);
        } catch (CustomException e) {
            log.error("VECTORIZE: CustomException — {} sourceType={} teamId={} apiPath={} resourceId={}",
                    e.getErrorCode().getMessage(),
                    job.sourceType(), job.teamId(), job.apiPath(), job.resourceId(), e);
        } catch (Exception e) {
            log.error("VECTORIZE: unexpected error — errorType={} sourceType={} teamId={} apiPath={} resourceId={} message='{}'",
                    e.getClass().getSimpleName(),
                    job.sourceType(), job.teamId(), job.apiPath(), job.resourceId(), e.getMessage(), e);
        }
    }

    private IndexingNormalizer resolveNormalizer(IndexSourceType sourceType) {
        IndexingNormalizer normalizer = normalizerMap.get(sourceType);
        if (normalizer == null) {
            throw new CustomException(IndexingErrorCode.INDEXING_NORMALIZER_NOT_FOUND);
        }
        return normalizer;
    }
}
