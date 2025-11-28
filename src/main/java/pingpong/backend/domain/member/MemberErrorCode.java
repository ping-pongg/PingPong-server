package pingpong.backend.domain.member;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum MemberErrorCode implements ApiErrorCode {

    MEMBER_NOT_FOUND("MEMBER404", "회원 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    USER_EMAIL_NOT_EXIST("MEMBER401", "가입된 이메일이 존재하지 않습니다.", HttpStatus.UNAUTHORIZED),
    USER_WRONG_PASSWORD("MEMBER401", "비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
    EMAIL_DUPLICATED("MEMBER409", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT);

    private final String errorCode;
    private final String message;
    private final HttpStatus status;
}

