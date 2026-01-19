package pingpong.backend.domain.team;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum TeamErrorCode implements ApiErrorCode {

    TEAM_NOT_FOUND("TEAM404", "팀 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    TEAM_MEMBER_ALREADY_EXISTS("TEAM409", "이미 해당 팀에 속한 회원입니다.", HttpStatus.CONFLICT);

    private final String errorCode;
    private final String message;
    private final HttpStatus status;
}

