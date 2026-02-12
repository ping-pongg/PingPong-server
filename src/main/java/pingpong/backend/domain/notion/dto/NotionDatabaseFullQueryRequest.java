package pingpong.backend.domain.notion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import pingpong.backend.domain.notion.enums.NotionQueryLogic;

import java.util.List;

@Schema(
        description = "전체 DB 조회용 제한 쿼리 바디 (스키마 독립, timestamp 기반만 허용)",
        example = """
                {
                  "logic": "AND",
                  "timestampFilters": [
                    {
                      "timestamp": "last_edited_time",
                      "operator": "on_or_after",
                      "value": "2026-01-01"
                    }
                  ],
                  "timestampSorts": [
                    {
                      "timestamp": "last_edited_time",
                      "direction": "descending"
                    }
                  ],
                  "pageSize": 50,
                  "startCursor": "cursor_value",
                  "includePages": true
                }"""
)
public record NotionDatabaseFullQueryRequest(
        @Schema(description = "타임스탬프 필터 로직 (기본 AND)", enumAsRef = true, example = "AND")
        NotionQueryLogic logic,

        @Valid
        @Schema(description = "타임스탬프 기반 필터만 허용 (프로퍼티 필터 불가)",
                example = """
                        [
                          {
                            "timestamp": "last_edited_time",
                            "operator": "on_or_after",
                            "value": "2026-01-01"
                          }
                        ]
                        """)
        List<NotionTimestampFilterRequest> timestampFilters,

        @Valid
        @Schema(description = "타임스탬프 기반 정렬만 허용 (프로퍼티 정렬 불가)",
                example = """
                        [
                          {
                            "timestamp": "last_edited_time",
                            "direction": "descending"
                          }
                        ]
                        """)
        List<NotionTimestampSortRequest> timestampSorts,

        @Min(1)
        @Max(100)
        @Schema(description = "페이지 크기 (1..100)", example = "50")
        Integer pageSize,

        @Schema(description = "시작 커서 (Notion query에서 받은 커서)", example = "cursor_value")
        String startCursor,

        @Schema(description = "결과 페이지 상세 조회 여부 (기본 true)", example = "true")
        Boolean includePages
) {
}
