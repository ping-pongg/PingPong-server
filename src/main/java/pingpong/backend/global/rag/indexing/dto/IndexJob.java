package pingpong.backend.global.rag.indexing.dto;

import com.fasterxml.jackson.databind.JsonNode;
import pingpong.backend.global.rag.indexing.enums.IndexSourceType;

public record IndexJob(
        IndexSourceType sourceType,
        Long teamId,
        String apiPath,
        String resourceId,
        JsonNode payload
) {
}
