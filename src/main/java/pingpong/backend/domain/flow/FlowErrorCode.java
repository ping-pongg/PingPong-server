package pingpong.backend.domain.flow;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum FlowErrorCode implements ApiErrorCode {

	FLOW_NOT_FOUND("FLOW404", "해당 플로우를 찾을 수 없어요.", HttpStatus.NOT_FOUND),
	FLOW_IMAGE_NOT_FOUND("FLOWIMAGE404", "해당 플로우 이미지를 찾을 수 없어요.", HttpStatus.NOT_FOUND),
	FLOW_REQUEST_NOT_FOUND("FLOWREQ404", "해당 플로우 요청을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
	FLOW_ACCESS_DENIED("FLOW403", "해당 플로우 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),

	// 업로드 완료 콜백 관련 (FlowImage 업로드 검증용)
	OBJECT_KEY_MISMATCH("FLOWIMAGE4001", "업로드 요청의 objectKey가 서버에 저장된 objectKey와 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
	S3_OBJECT_NOT_FOUND("FLOWIMAGE4002", "S3에 업로드된 파일을 찾을 수 없습니다. (업로드가 완료되지 않았을 수 있어요.)", HttpStatus.BAD_REQUEST),
	S3_HEAD_FAILED("FLOWIMAGE5001", "S3 파일 검증(HEAD 요청) 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
	UPLOAD_FAILED("FLOWIMAGE5002", "업로드에 실패했어요.", HttpStatus.INTERNAL_SERVER_ERROR);


	private final String errorCode;
	private final String message;
	private final HttpStatus status;
}
