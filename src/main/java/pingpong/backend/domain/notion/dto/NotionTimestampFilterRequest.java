package pingpong.backend.domain.notion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pingpong.backend.domain.notion.enums.NotionTimestampOperator;
import pingpong.backend.domain.notion.enums.NotionTimestampType;

@Schema(description = "타임스탬프 필터 (스키마 독립)")
public record NotionTimestampFilterRequest(
        @NotNull
        @Schema(description = "타임스탬프 종류", requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true,
                example = "last_edited_time")
        NotionTimestampType timestamp,

        @NotNull
        @Schema(description = "타임스탬프 연산자", requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true,
                example = "on_or_after")
        NotionTimestampOperator operator,

        @NotBlank
        @Schema(description = "타임스탬프 값 (ISO 날짜 또는 datetime)",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2026-01-01")
        String value
) {
}
