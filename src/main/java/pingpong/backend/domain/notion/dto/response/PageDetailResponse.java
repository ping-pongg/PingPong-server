package pingpong.backend.domain.notion.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import pingpong.backend.domain.notion.dto.common.PageDateRange;

import java.util.List;

/**
 * 페이지 상세 정보를 반환하는 응답 DTO
 * GET /pages/{pageId} endpoint에서 사용
 * 페이지 속성, 본문 내용, 자식 데이터베이스를 포함
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageDetailResponse(
        String id,            // 페이지 ID (dashes removed)
        String title,         // 페이지 제목
        PageDateRange date,   // 날짜 범위 (nullable)
        String status,        // 상태 (nullable)
        String pageContent,   // paragraph 블록들의 텍스트를 연결한 내용
        List<DatabaseWithPagesResponse> childDatabases  // 자식 데이터베이스 목록
) {
}
