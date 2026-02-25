package pingpong.backend.domain.swagger;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum SwaggerErrorCode implements ApiErrorCode {

	SWAGGER_CONNECTION_ERROR("SWAGGER500", "SWAGGER 연결에 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
	JSON_PROCESSING_EXCEPTION("SWAGGER501", "JSON 파싱에 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
	HASHING_EXCEPTION("SWAGGER502", "해싱 함수 처리 중 오류가 발생했어요.", HttpStatus.INTERNAL_SERVER_ERROR),

	//endpoint
	ENDPOINT_NOT_FOUND("ENDPOINT404", "해당 엔드포인트를 찾을 수 없어요.", HttpStatus.NOT_FOUND),
	ENDPOINT_NOT_ASSIGNED("ENDPOINT404", "해당 이미지에 할당된 엔드포인트를 찾을 수 없어요.", HttpStatus.NOT_FOUND);

	private final String errorCode;
	private final String message;
	private final HttpStatus status;
}