package pingpong.backend.domain.openAPI;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum SwaggerErrorCode implements ApiErrorCode {

	SWAGGER_CONNECTION_ERROR("SWAGGER500", "SWAGGER 연결에 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

	private final String errorCode;
	private final String message;
	private final HttpStatus status;
}