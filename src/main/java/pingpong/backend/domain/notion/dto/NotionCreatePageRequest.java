package pingpong.backend.domain.notion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "페이지 생성 요청 (DB 스키마 기반, 제공된 필드만 설정)")
public record NotionCreatePageRequest(
        @NotBlank
        @Schema(description = "페이지 제목 (DB title 속성에 매핑)",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "새 페이지")
        String title,

        @Valid
        @Schema(description = "날짜 범위 (DB date 속성에 매핑)",
                example = """
                        {
                          "start": "2026-02-10",
                          "end": null
                        }
                        """)
        NotionDateRange date,

        @Schema(description = "상태 옵션명 (DB status 속성에 매핑)",
                example = "진행 중")
        String status,

        @Schema(description = "자식 블록(선택, Notion 포맷 그대로 전달)",
                example = """
                        [
                          {
                            "object": "block",
                            "type": "paragraph",
                            "paragraph": {
                              "rich_text": []
                            }
                          }
                        ]
                        """)
        JsonNode children,

        @Schema(description = "아이콘(선택, Notion 포맷 그대로 전달)",
                example = """
                        {
                          "type": "emoji",
                          "emoji": "\ud83d\udcdd"
                        }
                        """)
        JsonNode icon,

        @Schema(description = "커버(선택, Notion 포맷 그대로 전달)",
                example = """
                        {
                          "type": "external",
                          "external": {
                            "url": "https://example.com/cover.png"
                          }
                        }
                        """)
        JsonNode cover
) {
}
