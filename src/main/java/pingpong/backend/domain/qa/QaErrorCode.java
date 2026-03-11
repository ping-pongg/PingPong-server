package pingpong.backend.domain.qa;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum QaErrorCode implements ApiErrorCode {

	QA_NOT_FOUND("QA404", "해당 QA를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	QA_JSON_PROCESSING_ERROR("QA500", "QA 데이터 직렬화 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
	AI_CALL_FAILED("QA501", "AI 서비스 호출에 실패했습니다. (외부 API 오류)", HttpStatus.BAD_GATEWAY),
	AI_RESPONSE_PARSING_ERROR("QA502", "AI 응답 데이터의 형식이 올바르지 않아 처리에 실패했습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
	DATA_SERIALIZATION_FAILED("QA500", "분석용 데이터 변환 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

	private final String errorCode;
	private final String message;
	private final HttpStatus status;
}
