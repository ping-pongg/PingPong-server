package pingpong.backend.domain.notion.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 페이지의 날짜 범위를 나타내는 응답 DTO
 * Notion API의 date property에서 추출한 start/end 날짜
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageDateRange(
        String start,  // 시작 날짜 (nullable)
        String end     // 종료 날짜 (nullable)
) {
}
