package pingpong.backend.domain.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum ChatErrorCode implements ApiErrorCode {

    CHAT_AI_CALL_FAILED("CHAT500", "AI 응답 생성 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    STREAM_NOT_FOUND("CHAT404", "스트림을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    STREAM_ACCESS_DENIED("CHAT403", "스트림 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    STREAM_ALREADY_COMPLETED("CHAT409", "이미 완료된 스트림입니다.", HttpStatus.CONFLICT),
    STREAM_INITIALIZATION_FAILED("CHAT500_INIT", "스트림 초기화에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String errorCode;
    private final String message;
    private final HttpStatus status;
}
