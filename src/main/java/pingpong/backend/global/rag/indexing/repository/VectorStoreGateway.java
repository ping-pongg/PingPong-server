package pingpong.backend.global.rag.indexing.repository;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class VectorStoreGateway {

    private final VectorStore vectorStore;
    private final IndexingStateRepository stateRepository;

    @Transactional
    public void upsert(IndexJob job, List<String> chunks, String normalizedText) {
        String sourceKey = buildSourceKey(job);
        String contentHash = sha256Hex(normalizedText);
        String documentPrefix = sha256Hex(sourceKey).substring(0, 32);

        Optional<IndexingState> stateOptional = stateRepository.findBySourceKey(sourceKey);
        if (stateOptional.isPresent() && contentHash.equals(stateOptional.get().getContentHash())) {
            return;
        }

        IndexingState state = stateOptional.orElse(null);

        deleteStaleChunks(state, documentPrefix, chunks.size());
        vectorStore.add(toDocuments(job, sourceKey, documentPrefix, chunks));

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
        log.info("INDEX: upserted sourceType={} teamId={} apiPath={} resourceId={} chunks={}",
                job.sourceType(), job.teamId(), job.apiPath(), job.resourceId(), chunks.size());
    }

    public List<Document> query(IndexQueryOptions options) {
        if (options == null || options.query() == null || options.query().isBlank()) {
            return List.of();
        }

        SearchRequest.Builder builder = SearchRequest.builder().query(options.query());
        if (options.topK() != null && options.topK() > 0) {
            builder.topK(options.topK());
        }

        String filterExpression = buildFilterExpression(options);
        if (!filterExpression.isBlank()) {
            builder.filterExpression(filterExpression);
        }

        return vectorStore.similaritySearch(builder.build());
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
            filters.add("apiPath == '" + escape(options.apiPath()) + "'");
        }
        if (options.databaseId() != null && !options.databaseId().isBlank()) {
            filters.add("databaseId == '" + escape(options.databaseId()) + "'");
        }
        if (options.pageId() != null && !options.pageId().isBlank()) {
            filters.add("pageId == '" + escape(options.pageId()) + "'");
        }
        if (options.lastEditedAfter() != null) {
            filters.add("lastEditedTime >= '" + options.lastEditedAfter().toString() + "'");
        }

        return String.join(" && ", filters);
    }

    private List<Document> toDocuments(IndexJob job,
                                       String sourceKey,
                                       String documentPrefix,
                                       List<String> chunks) {
        List<Document> documents = new ArrayList<>(chunks.size());
        Instant now = Instant.now();

        JsonNode payload = job.payload();
        String databaseId = extractDatabaseId(payload);
        String pageId = extractPageId(job, payload);
        String title = extractTitle(payload);
        String lastEditedTime = extractLastEditedTime(payload);
        String firstBlockId = extractFirstBlockId(payload);

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sourceType", job.sourceType().name());
            metadata.put("teamId", job.teamId());
            metadata.put("apiPath", job.apiPath());
            metadata.put("sourceKey", sourceKey);
            metadata.put("databaseId", defaultString(databaseId));
            metadata.put("pageId", defaultString(pageId));
            metadata.put("blockId", defaultString(firstBlockId));
            metadata.put("title", defaultString(title));
            metadata.put("lastEditedTime", defaultString(lastEditedTime));
            metadata.put("position", i);
            metadata.put("depth", inferDepth(chunk));
            metadata.put("chunkIndex", i);
            metadata.put("chunkCount", chunks.size());
            metadata.put("updatedAt", now.toString());

            Document document = Document.builder()
                    .id(documentPrefix + "-" + i)
                    .text(chunk)
                    .metadata(metadata)
                    .build();
            documents.add(document);
        }

        return documents;
    }

    private int inferDepth(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return 0;
        }
        String[] lines = chunk.split("\\R");
        int maxDepth = 0;
        for (String line : lines) {
            int spaces = 0;
            while (spaces < line.length() && line.charAt(spaces) == ' ') {
                spaces++;
            }
            int depth = spaces / 2;
            if (depth > maxDepth) {
                maxDepth = depth;
            }
        }
        return maxDepth;
    }

    private String extractDatabaseId(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return "";
        }
        String id = payload.path("database").path("id").asText("");
        if (!id.isBlank()) {
            return id;
        }

        JsonNode childDatabases = payload.path("child_databases");
        if (childDatabases.isArray() && !childDatabases.isEmpty()) {
            return childDatabases.get(0).path("database").path("id").asText("");
        }
        return "";
    }

    private String extractPageId(IndexJob job, JsonNode payload) {
        if (job.resourceId() != null && !job.resourceId().isBlank()) {
            return job.resourceId();
        }
        if (payload == null || payload.isNull()) {
            return "";
        }
        JsonNode results = payload.path("query_result").path("results");
        if (results.isArray() && !results.isEmpty()) {
            return results.get(0).path("id").asText("");
        }
        return "";
    }

    private String extractTitle(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return "";
        }
        String title = payload.path("database").path("title").path(0).path("plain_text").asText("");
        if (!title.isBlank()) {
            return title;
        }

        JsonNode pages = payload.path("query_result").path("results");
        if (pages.isArray() && !pages.isEmpty()) {
            return pages.get(0).path("url").asText("");
        }
        return "";
    }

    private String extractLastEditedTime(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return "";
        }
        String databaseEdited = payload.path("database").path("last_edited_time").asText("");
        if (!databaseEdited.isBlank()) {
            return databaseEdited;
        }

        JsonNode pageResults = payload.path("query_result").path("results");
        if (pageResults.isArray() && !pageResults.isEmpty()) {
            String pageEdited = pageResults.get(0).path("last_edited_time").asText("");
            if (!pageEdited.isBlank()) {
                return pageEdited;
            }
        }

        return "";
    }

    private String extractFirstBlockId(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return "";
        }
        JsonNode results = payload.path("results");
        if (results.isArray() && !results.isEmpty()) {
            return results.get(0).path("id").asText("");
        }
        return "";
    }

    private void deleteStaleChunks(IndexingState state, String currentDocumentPrefix, int newChunkCount) {
        if (state == null || state.getChunkCount() <= 0) {
            return;
        }

        List<String> staleIds = new ArrayList<>();
        if (!state.getDocumentPrefix().equals(currentDocumentPrefix)) {
            for (int i = 0; i < state.getChunkCount(); i++) {
                staleIds.add(state.getDocumentPrefix() + "-" + i);
            }
        } else if (state.getChunkCount() > newChunkCount) {
            for (int i = newChunkCount; i < state.getChunkCount(); i++) {
                staleIds.add(currentDocumentPrefix + "-" + i);
            }
        }

        if (!staleIds.isEmpty()) {
            vectorStore.delete(staleIds);
        }
    }

    private String buildSourceKey(IndexJob job) {
        StringBuilder key = new StringBuilder();
        key.append(job.sourceType().name()).append('|').append(job.teamId()).append('|').append(job.apiPath());
        if (job.resourceId() != null && !job.resourceId().isBlank()) {
            key.append('|').append(job.resourceId());
        }
        return key.toString();
    }

    private String escape(String value) {
        return value.replace("'", "\\'");
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String value = Integer.toHexString(b & 0xff);
                if (value.length() == 1) {
                    hex.append('0');
                }
                hex.append(value);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash indexing content", e);
        }
    }
}
