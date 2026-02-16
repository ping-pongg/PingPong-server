package pingpong.backend.domain.notion.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import pingpong.backend.domain.notion.dto.common.PageDateRange;

/**
 * 데이터베이스 쿼리 결과에서 반환되는 페이지 요약 정보
 * id, 제목, 날짜, 상태만 포함
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageSummary(
        String id,       // 페이지 ID (dashes removed)
        String title,    // 페이지 제목
        PageDateRange date,  // 날짜 범위 (nullable)
        String status    // 상태 (nullable)
) {
}
