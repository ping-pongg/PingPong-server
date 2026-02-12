package pingpong.backend.domain.notion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notion OAuth 교환 응답")
public record NotionOAuthExchangeResponse(
        @Schema(description = "연결 여부", example = "true")
        boolean connected,
        @Schema(description = "워크스페이스 ID", example = "workspace_id_123")
        String workspaceId,
        @Schema(description = "워크스페이스 이름", example = "팀 워크스페이스")
        String workspaceName,
        @Schema(description = "대표 데이터베이스 ID", nullable = true, example = "db_id_123")
        String databaseId,
        @Schema(description = "대표 데이터베이스 선택 여부", example = "true")
        boolean databaseSelected
) {
}
