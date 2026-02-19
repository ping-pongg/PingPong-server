package pingpong.backend.global.rag.indexing.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.global.rag.indexing.IndexingState;
import pingpong.backend.global.rag.indexing.dto.IndexJob;
import pingpong.backend.global.rag.indexing.dto.IndexQueryOptions;
import pingpong.backend.global.rag.indexing.enums.IndexSourceType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class VectorStoreGateway {

    private final VectorStore vectorStore;
    private final IndexingStateRepository stateRepository;
    private final DocumentFactory documentFactory;

    /**
     * 벡터 DB에 문서를 upsert합니다.
     * vectorStore.add()는 벡터 DB 연산으로 JPA 트랜잭션 범위 밖에 있으며,
     * {@literal @}Transactional은 stateRepository.save()의 일관성을 보장하기 위해 유지합니다.
     */
    @Transactional
    public void upsert(IndexJob job, List<String> chunks, String normalizedText) {
        String sourceKey = documentFactory.buildSourceKey(job);
        String contentHash = documentFactory.sha256Hex(normalizedText);
        String documentPrefix = documentFactory.documentPrefix(sourceKey);

        log.info("INDEX-UPSERT: sourceType={} teamId={} apiPath={} resourceId={} chunks={} sourceKey={}",
                job.sourceType(), job.teamId(), job.apiPath(), job.resourceId(), chunks.size(), sourceKey);

        Optional<IndexingState> stateOptional = stateRepository.findBySourceKey(sourceKey);
        if (stateOptional.isPresent() && contentHash.equals(stateOptional.get().getContentHash())) {
            log.info("INDEX-UPSERT: content unchanged (hash match), skipping vectorStore.add — sourceType={} teamId={} resourceId={}",
                    job.sourceType(), job.teamId(), job.resourceId());
            return;
        }

        IndexingState state = stateOptional.orElse(null);

        deleteStaleChunks(state, documentPrefix, chunks.size());

        try {
            log.info("INDEX-UPSERT: calling vectorStore.add() with {} chunks for sourceKey={}", chunks.size(), sourceKey);
            vectorStore.add(documentFactory.toDocuments(job, sourceKey, documentPrefix, chunks));
            log.info("INDEX-UPSERT: vectorStore.add() succeeded for sourceKey={}", sourceKey);
        } catch (Exception e) {
            log.error("INDEX-UPSERT: vectorStore.add() FAILED — sourceType={} teamId={} apiPath={} resourceId={} sourceKey={} error='{}'",
                    job.sourceType(), job.teamId(), job.apiPath(), job.resourceId(), sourceKey, e.getMessage(), e);
            throw e;
        }

        Instant now = Instant.now();
        if (state == null) {
            state = IndexingState.create(
                    job.sourceType(),
                    job.teamId(),
                    job.apiPath(),
                    job.resourceId(),
                    sourceKey,
                    documentPrefix,
                    contentHash,
                    chunks.size(),
                    now
            );
        } else {
            state.refresh(job.sourceType(), job.apiPath(), job.resourceId(), contentHash, chunks.size(), now);
        }

        stateRepository.save(state);
        log.info("INDEX-UPSERT: complete — sourceType={} teamId={} apiPath={} resourceId={} chunks={}",
                job.sourceType(), job.teamId(), job.apiPath(), job.resourceId(), chunks.size());
    }

    /**
     * page.deleted 이벤트 처리용: 삭제된 페이지의 VectorDB 청크와 IndexingState를 제거합니다.
     */
    @Transactional
    public void deleteByPageId(Long teamId, String pageId) {
        stateRepository.findBySourceTypeAndTeamIdAndResourceId(IndexSourceType.NOTION, teamId, pageId)
                .ifPresent(state -> {
                    List<String> ids = new ArrayList<>();
                    for (int i = 0; i < state.getChunkCount(); i++) {
                        ids.add(state.getDocumentPrefix() + "-" + i);
                    }
                    if (!ids.isEmpty()) {
                        vectorStore.delete(ids);
                    }
                    stateRepository.delete(state);
                    log.info("INDEX: deleted pageId={} teamId={} chunks={}", pageId, teamId, ids.size());
                });
    }

    public List<Document> query(IndexQueryOptions options) {
        if (options == null || options.query() == null || options.query().isBlank()) {
            log.warn("VECTOR-QUERY: query is null or blank — returning empty result");
            return List.of();
        }

        String queryPreview = options.query().length() > 80
                ? options.query().substring(0, 80) + "..." : options.query();
        log.info("VECTOR-QUERY: starting — query='{}' topK={} teamId={} sourceType={}",
                queryPreview, options.topK(), options.teamId(), options.sourceType());

        SearchRequest.Builder builder = SearchRequest.builder().query(options.query());
        if (options.topK() != null && options.topK() > 0) {
            builder.topK(options.topK());
        }

        String filterExpression = buildFilterExpression(options);
        log.info("VECTOR-QUERY: filter expression='{}'", filterExpression);
        if (!filterExpression.isBlank()) {
            builder.filterExpression(filterExpression);
        }

        List<Document> results;
        try {
            results = vectorStore.similaritySearch(builder.build());
        } catch (Exception e) {
            log.error("VECTOR-QUERY: vectorStore.similaritySearch() FAILED — filter='{}' query='{}' error='{}'",
                    filterExpression, queryPreview, e.getMessage(), e);
            throw e;
        }

        if (results == null || results.isEmpty()) {
            log.warn("VECTOR-QUERY: NO documents found — teamId={} filter='{}' (데이터 인덱싱 여부 또는 필터/유사도 임계값 확인 필요)",
                    options.teamId(), filterExpression);
        } else {
            log.info("VECTOR-QUERY: found {} document(s) — teamId={} filter='{}'",
                    results.size(), options.teamId(), filterExpression);
            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                log.debug("VECTOR-QUERY: result[{}] id={} score={} sourceKey={} apiPath={}",
                        i, doc.getId(),
                        doc.getScore(),
                        doc.getMetadata().get("sourceKey"),
                        doc.getMetadata().get("apiPath"));
            }
        }

        return results;
    }

    private String buildFilterExpression(IndexQueryOptions options) {
        List<String> filters = new ArrayList<>();

        if (options.sourceType() != null) {
            filters.add("sourceType == '" + options.sourceType().name() + "'");
        }
        if (options.teamId() != null) {
            filters.add("teamId == " + options.teamId());
        }
        if (options.apiPath() != null && !options.apiPath().isBlank()) {
            filters.add("apiPath == '" + documentFactory.escape(options.apiPath()) + "'");
        }
        if (options.databaseId() != null && !options.databaseId().isBlank()) {
            filters.add("databaseId == '" + documentFactory.escape(options.databaseId()) + "'");
        }
        if (options.pageId() != null && !options.pageId().isBlank()) {
            filters.add("pageId == '" + documentFactory.escape(options.pageId()) + "'");
        }
        if (options.lastEditedAfter() != null) {
            filters.add("lastEditedTime >= '" + options.lastEditedAfter().toString() + "'");
        }

        return String.join(" && ", filters);
    }

    private void deleteStaleChunks(IndexingState state, String currentDocumentPrefix, int newChunkCount) {
        if (state == null || state.getChunkCount() <= 0) {
            return;
        }

        List<String> staleIds = new ArrayList<>();
        if (!state.getDocumentPrefix().equals(currentDocumentPrefix)) {
            log.info("INDEX-STALE: documentPrefix changed (old={}, new={}) — deleting all {} old chunks",
                    state.getDocumentPrefix(), currentDocumentPrefix, state.getChunkCount());
            for (int i = 0; i < state.getChunkCount(); i++) {
                staleIds.add(state.getDocumentPrefix() + "-" + i);
            }
        } else if (state.getChunkCount() > newChunkCount) {
            int staleCount = state.getChunkCount() - newChunkCount;
            log.info("INDEX-STALE: chunk count reduced ({} → {}) — deleting {} trailing chunks for prefix={}",
                    state.getChunkCount(), newChunkCount, staleCount, currentDocumentPrefix);
            for (int i = newChunkCount; i < state.getChunkCount(); i++) {
                staleIds.add(currentDocumentPrefix + "-" + i);
            }
        }

        if (!staleIds.isEmpty()) {
            try {
                vectorStore.delete(staleIds);
                log.info("INDEX-STALE: deleted {} stale chunk(s) from vectorStore", staleIds.size());
            } catch (Exception e) {
                log.error("INDEX-STALE: vectorStore.delete() FAILED for {} chunk(s) error='{}'",
                        staleIds.size(), e.getMessage(), e);
                throw e;
            }
        }
    }
}
