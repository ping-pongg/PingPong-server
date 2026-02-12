package pingpong.backend.domain.notion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record NotionOAuthTokenResponse(
        @JsonProperty("access_token")
        @Schema(description = "액세스 토큰", example = "access_token_abc")
        String accessToken,
        @JsonProperty("refresh_token")
        @Schema(description = "리프레시 토큰", example = "refresh_token_def")
        String refreshToken,
        @JsonProperty("workspace_id")
        @Schema(description = "워크스페이스 ID", example = "workspace_id_123")
        String workspaceId,
        @JsonProperty("workspace_name")
        @Schema(description = "워크스페이스 이름", example = "팀 워크스페이스")
        String workspaceName,
        @JsonProperty("bot_id")
        @Schema(description = "봇 ID", example = "bot_id_123")
        String botId
) {
}
