package pingpong.backend.global.rag.indexing.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import pingpong.backend.global.exception.CustomException;
import pingpong.backend.global.rag.indexing.dto.IndexJob;
import pingpong.backend.global.rag.indexing.IndexingErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DocumentFactory {

    private static final int HASH_PREFIX_LENGTH = 32;

    public List<Document> toDocuments(IndexJob job,
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

    public String buildSourceKey(IndexJob job) {
        StringBuilder key = new StringBuilder();
        key.append(job.sourceType().name()).append('|').append(job.teamId()).append('|').append(job.apiPath());
        if (job.resourceId() != null && !job.resourceId().isBlank()) {
            key.append('|').append(job.resourceId());
        }
        return key.toString();
    }

    public String sha256Hex(String input) {
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
            throw new CustomException(IndexingErrorCode.INDEXING_VECTORIZE_FAILED);
        }
    }

    public String documentPrefix(String sourceKey) {
        return sha256Hex(sourceKey).substring(0, HASH_PREFIX_LENGTH);
    }

    public String escape(String value) {
        return value.replace("'", "\\'");
    }

    int inferDepth(String chunk) {
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

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
