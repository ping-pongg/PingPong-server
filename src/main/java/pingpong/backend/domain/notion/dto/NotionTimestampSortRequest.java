package pingpong.backend.domain.notion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import pingpong.backend.domain.notion.enums.NotionSortDirection;
import pingpong.backend.domain.notion.enums.NotionTimestampType;

@Schema(description = "타임스탬프 정렬 (스키마 독립)")
public record NotionTimestampSortRequest(
        @NotNull
        @Schema(description = "타임스탬프 종류", requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true,
                example = "created_time")
        NotionTimestampType timestamp,

        @NotNull
        @Schema(description = "정렬 방향", requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true,
                example = "descending")
        NotionSortDirection direction
) {
}
