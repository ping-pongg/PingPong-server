package pingpong.backend.domain.notion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum NotionErrorCode implements ApiErrorCode {

    NOTION_NOT_CONNECTED("NOTION404", "Notion이 연결되어 있지 않습니다.", HttpStatus.NOT_FOUND),
    NOTION_DATABASE_NOT_SET("NOTION424", "대표 Notion 데이터베이스가 설정되지 않았습니다.", HttpStatus.FAILED_DEPENDENCY),
    NOTION_INVALID_QUERY("NOTION400", "Notion 쿼리 요청이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    NOTION_TOKEN_REFRESH_FAILED("NOTION401", "Notion 토큰 갱신에 실패했습니다. 다시 연결해주세요.", HttpStatus.UNAUTHORIZED),
    NOTION_PERMISSION_DENIED("NOTION403", "Notion 권한이 부족합니다.", HttpStatus.FORBIDDEN),
    NOTION_API_ERROR("NOTION502", "Notion API 요청에 실패했습니다.", HttpStatus.BAD_GATEWAY);

    private final String errorCode;
    private final String message;
    private final HttpStatus status;
}
