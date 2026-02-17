package pingpong.backend.domain.notion.dto.response;

/**
 * child database 쿼리 결과에서 반환되는 페이지 요약 정보
 * id, url, 제목, 상태만 포함
 */
public record ChildPageSummary(
        String id,       // 페이지 ID (dashes removed)
        String url,      // 페이지 URL
        String title,    // 페이지 제목
        String status    // 상태 (nullable)
) {
}
