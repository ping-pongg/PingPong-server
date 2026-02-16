package pingpong.backend.global.rag.indexing.dto;

import pingpong.backend.global.rag.indexing.enums.IndexSourceType;

import java.time.Instant;

public record IndexQueryOptions(
        String query,
        Integer topK,
        IndexSourceType sourceType,
        Long teamId,
        String apiPath,
        String databaseId,
        String pageId,
        Instant lastEditedAfter
) {
}
