package pingpong.backend.domain.notion.dto.response;

import java.util.List;

/**
 * 데이터베이스와 그 안의 페이지 목록을 반환하는 응답 DTO
 * GET /databases/primary endpoint에서 사용
 */
public record DatabaseWithPagesResponse(
        String databaseTitle,  // 데이터베이스 제목
        List<PageSummary> pages  // 페이지 목록
) {
}
