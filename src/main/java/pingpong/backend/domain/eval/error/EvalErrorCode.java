package pingpong.backend.domain.eval.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum EvalErrorCode implements ApiErrorCode {

    EVAL_NOT_FOUND("EVAL404", "평가 케이스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_METRIC("EVAL400", "지원하지 않는 metric 파라미터입니다.", HttpStatus.BAD_REQUEST),
    INVALID_INTERVAL("EVAL400_INT", "interval은 'day' 또는 'hour'만 허용됩니다.", HttpStatus.BAD_REQUEST),
    JUDGE_FAILED("EVAL500", "Judge LLM 호출에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String errorCode;
    private final String message;
    private final HttpStatus status;
}
