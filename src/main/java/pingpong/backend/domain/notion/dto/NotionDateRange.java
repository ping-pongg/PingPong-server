package pingpong.backend.domain.notion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "날짜 범위 객체")
public record NotionDateRange(
        @NotBlank
        @Pattern(
                regexp = "^(\\d{4}-\\d{2}-\\d{2})(T.*)?$",
                message = "start must be ISO-8601 date or datetime"
        )
        @Schema(description = "시작일 (YYYY-MM-DD 또는 ISO-8601 datetime)",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2026-02-10")
        String start,

        @Pattern(
                regexp = "^$|^(\\d{4}-\\d{2}-\\d{2})(T.*)?$",
                message = "end must be ISO-8601 date or datetime"
        )
        @Schema(description = "종료일 (YYYY-MM-DD 또는 ISO-8601 datetime)",
                nullable = true,
                example = "2026-02-12")
        String end
) {
}
