package pingpong.backend.domain.server;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum ServerErrorCode implements ApiErrorCode {

	SERVER_NOT_FOUND("SERVER404", "해당 엔드포인트를 찾을 수 없어요.", HttpStatus.NOT_FOUND),
	FORBIDDEN("SERVER403", "해당 서버의 접근 권한이 없어요", HttpStatus.FORBIDDEN);

	private final String errorCode;
	private final String message;
	private final HttpStatus status;
}
