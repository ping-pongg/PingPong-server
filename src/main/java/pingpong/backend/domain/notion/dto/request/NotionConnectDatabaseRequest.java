package pingpong.backend.domain.notion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "팀 대표 Notion 데이터베이스 연결 요청")
public record NotionConnectDatabaseRequest(
        @Schema(description = "연결할 Notion 데이터베이스 ID (기존 연결 덮어쓰기)",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "b8f4a9f0-1234-5678-90ab-cdef12345678")
        @NotBlank
        String databaseId
) {
}
