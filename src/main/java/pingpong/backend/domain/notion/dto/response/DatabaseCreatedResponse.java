package pingpong.backend.domain.notion.dto.response;

/**
 * 데이터베이스 생성 결과를 반환하는 응답 DTO
 * POST /pages/{pageId}/databases endpoint에서 사용
 */
public record DatabaseCreatedResponse(
        String id,     // 생성된 데이터베이스 ID (dashes removed)
        String title,  // 데이터베이스 제목
        String url     // 데이터베이스 URL
) {
}
