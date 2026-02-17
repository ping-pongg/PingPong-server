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

        boolean isPageDoc = job.apiPath() != null && job.apiPath().contains("/notion/pages/");
        boolean isDatabaseDoc = job.apiPath() != null && job.apiPath().contains("/databases/primary");

        String status = extractStatus(payload);
        String startDate = extractStartDate(payload);
        String endDate = extractEndDate(payload);
        String pageUrl = extractPageUrl(payload, isPageDoc);
        String databaseTitle = extractDatabaseTitle(payload, isDatabaseDoc);
        int pageCount = extractPageCount(payload, isDatabaseDoc);

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
            if (isPageDoc) {
                metadata.put("status", defaultString(status));
                metadata.put("startDate", defaultString(startDate));
                metadata.put("endDate", defaultString(endDate));
                metadata.put("pageUrl", defaultString(pageUrl));
            }
            if (isDatabaseDoc) {
                metadata.put("databaseTitle", defaultString(databaseTitle));
                metadata.put("pageCount", pageCount);
            }

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

    // -------------------------------------------------------------------------
    // DTO JSON 구조 기반 필드 추출
    // DatabaseWithPagesResponse: { "databaseTitle": "...", "pages": [...] }
    // PageDetailResponse: { "id", "url", "title", "date": {"start","end"}, "status", "pageContent", "childDatabases": [...] }
    // -------------------------------------------------------------------------

    private String extractDatabaseId(JsonNode payload) {
        // DTO 구조에는 databaseId 필드가 없음 — 빈 문자열 반환
        return "";
    }

    private String extractPageId(IndexJob job, JsonNode payload) {
        if (job.resourceId() != null && !job.resourceId().isBlank()) {
            return job.resourceId();
        }
        if (payload == null || payload.isNull()) {
            return "";
        }
        // PageDetailResponse: root.id
        return payload.path("id").asText("");
    }

    private String extractTitle(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return "";
        }
        // PageDetailResponse: root.title
        String pageTitle = payload.path("title").asText("").trim();
        if (!pageTitle.isBlank()) {
            return pageTitle;
        }
        // DatabaseWithPagesResponse: root.databaseTitle
        return payload.path("databaseTitle").asText("").trim();
    }

    private String extractLastEditedTime(JsonNode payload) {
        // DTO에는 lastEditedTime 필드가 없음 — 빈 문자열 반환
        return "";
    }

    private String extractFirstBlockId(JsonNode payload) {
        // DTO에는 blockId 필드가 없음 — 빈 문자열 반환
        return "";
    }

    private String extractStatus(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return "";
        }
        return payload.path("status").asText("").trim();
    }

    private String extractStartDate(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return "";
        }
        return payload.path("date").path("start").asText("").trim();
    }

    private String extractEndDate(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return "";
        }
        return payload.path("date").path("end").asText("").trim();
    }

    private String extractPageUrl(JsonNode payload, boolean isPageDoc) {
        if (!isPageDoc || payload == null || payload.isNull()) {
            return "";
        }
        return payload.path("url").asText("").trim();
    }

    private String extractDatabaseTitle(JsonNode payload, boolean isDatabaseDoc) {
        if (!isDatabaseDoc || payload == null || payload.isNull()) {
            return "";
        }
        return payload.path("databaseTitle").asText("").trim();
    }

    private int extractPageCount(JsonNode payload, boolean isDatabaseDoc) {
        if (!isDatabaseDoc || payload == null || payload.isNull()) {
            return 0;
        }
        JsonNode pages = payload.path("pages");
        return pages.isArray() ? pages.size() : 0;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
