package pingpong.backend.domain.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum ChatErrorCode implements ApiErrorCode {

    CHAT_AI_CALL_FAILED("CHAT500", "AI 응답 생성 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String errorCode;
    private final String message;
    private final HttpStatus status;
}
