package pingpong.backend.domain.notion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Notion OAuth 코드 교환 요청")
public record NotionOAuthExchangeRequest(
        @NotBlank
        @Schema(description = "Notion OAuth 코드",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "oauth_code_123")
        String code,
        @NotBlank
        @Schema(description = "OAuth 리다이렉트 URI",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "https://pingpong-team.vercel.app")
        String redirectUri
) {
}
