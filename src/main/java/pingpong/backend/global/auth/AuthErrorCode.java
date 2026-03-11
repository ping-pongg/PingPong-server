package pingpong.backend.global.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements ApiErrorCode {

    REFRESH_TOKEN_INVALID("TOKEN401", "유효하지 않은 Refresh Token 입니다.", HttpStatus.UNAUTHORIZED),
    INVALID_OAUTH_CODE("OAUTH400", "유효하지 않은 OAuth 코드입니다.", HttpStatus.BAD_REQUEST),
    INVALID_REDIRECT_URI("OAUTH403", "등록되지 않은 redirect_uri입니다.", HttpStatus.BAD_REQUEST),
    NOT_TEAM_MEMBER("OAUTH405", "해당 팀의 멤버가 아닙니다.", HttpStatus.FORBIDDEN);

    private final String errorCode;
    private final String message;
    private final HttpStatus status;
}

