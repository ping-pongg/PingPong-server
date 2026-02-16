package pingpong.backend.domain.notion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import pingpong.backend.domain.notion.dto.common.NotionDateRange;

@Schema(
        description = "페이지 생성 요청",
        example = """
                {
                  "title": "새 페이지",
                  "date": {
                    "start": "2026-02-10",
                    "end": "2026-02-15"
                  },
                  "status": "진행 중"
                }
                """
)
public record NotionCreatePageRequest(
        @NotBlank
        @Schema(
                description = "페이지 제목",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "새 페이지"
        )
        String title,

        @Valid
        @Schema(
                description = "날짜 범위 (시작일, 종료일)",
                example = """
                        {
                          "start": "2026-02-10",
                          "end": "2026-02-15"
                        }
                        """
        )
        NotionDateRange date,

        @Schema(
                description = "상태",
                example = "진행 중"
        )
        String status
) {
}
