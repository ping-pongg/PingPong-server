package pingpong.backend.domain.notion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import pingpong.backend.domain.notion.dto.common.NotionDateRange;

@Schema(description = "Notion 페이지 수정 요청 (제공된 필드만 수정)")
public record NotionPageUpdateRequest(
        @Schema(description = "새 페이지 제목", example = "요구사항 반영")
        String title,

        @Valid
        @Schema(description = "계획일 범위 (계획일 속성에 매핑)",
                example = """
                        {
                          "start": "2026-02-10",
                          "end": "2026-02-12"
                        }
                        """)
        NotionDateRange date,

        @Valid
        @Schema(description = "완료일 범위 (완료일 속성에 매핑)",
                example = """
                        {
                          "start": "2026-03-01",
                          "end": null
                        }
                        """)
        NotionDateRange completedDate,

        @Schema(description = "상태 옵션명 (status 속성에 매핑)", example = "완료")
        String status
) {
}
