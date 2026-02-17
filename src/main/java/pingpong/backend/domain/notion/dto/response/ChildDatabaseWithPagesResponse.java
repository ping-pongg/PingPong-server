package pingpong.backend.domain.notion.dto.response;

import java.util.List;

/**
 * child database와 그 안의 페이지 목록을 반환하는 응답 DTO
 * 페이지 상세 응답의 childDatabases에서 사용
 */
public record ChildDatabaseWithPagesResponse(
        String databaseTitle,  // 데이터베이스 제목
        List<ChildPageSummary> pages  // 페이지 목록
) {
}
