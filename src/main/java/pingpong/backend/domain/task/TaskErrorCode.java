package pingpong.backend.domain.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum TaskErrorCode implements ApiErrorCode {

    TASK_NOT_FOUND("TASK404", "해당 태스크를 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    FLOW_NOT_FOUND_IN_MAPPING("TASK_FLOW404", "매핑하려는 Flow가 존재하지 않습니다.", HttpStatus.NOT_FOUND),
    NO_FLOW_MAPPED("TASK400", "매핑된 Flow가 없어 완료 처리할 수 없습니다.", HttpStatus.BAD_REQUEST);

    private final String errorCode;
    private final String message;
    private final HttpStatus status;
}
