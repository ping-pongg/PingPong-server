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
	FLOW_ACCESS_DENIED("FLOW403", "해당 플로우 접근 권한이 없습니다.", HttpStatus.FORBIDDEN);

	private final String errorCode;
	private final String message;
	private final HttpStatus status;
}
