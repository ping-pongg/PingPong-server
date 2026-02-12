package pingpong.backend.domain.notion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "특정 DB 조회용 자유 쿼리 바디 (Notion query 포맷 그대로 전달)",
        example = """
                {
                  "filter": {
                    "property": "Status",
                    "status": {
                      "equals": "Done"
                    }
                  },
                  "sorts": [
                    {
                      "property": "Date",
                      "direction": "descending"
                    }
                  ],
                  "page_size": 50,
                  "start_cursor": "cursor_value",
                  "include_pages": true
                }"""
)
public record NotionDatabaseQueryRequest(
        @Schema(description = "Notion 필터 객체 (원본 포맷)", example = """
                {
                  "property": "Status",
                  "status": {
                    "equals": "Done"
                  }
                }
                """)
        @JsonProperty("filter")
        JsonNode filter,

        @Schema(description = "Notion 정렬 배열 (원본 포맷)", example = """
                [
                  {
                    "property": "Date",
                    "direction": "descending"
                  }
                ]
                """)
        @JsonProperty("sorts")
        JsonNode sorts,

        @Schema(description = "페이지 크기", example = "50")
        @JsonProperty("page_size")
        Integer pageSize,

        @Schema(description = "시작 커서 (Notion query에서 받은 커서)", example = "cursor_value")
        @JsonProperty("start_cursor")
        String startCursor,

        @Schema(description = "결과 페이지 상세 조회 여부 (기본 true)", example = "true")
        @JsonProperty("include_pages")
        Boolean includePages
) {
}
