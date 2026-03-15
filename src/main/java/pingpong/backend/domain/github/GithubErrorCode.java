package pingpong.backend.domain.github;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pingpong.backend.global.exception.ApiErrorCode;

@Getter
@AllArgsConstructor
public enum GithubErrorCode implements ApiErrorCode {

	REPOSITORY_NOT_FOUND("GITHUB404", "해당 레포지토리 또는 브랜치를 찾을 수 없어요.", HttpStatus.NOT_FOUND),
	GITHUB_CONFIG_CONFLICT("GITHUB409", "해당 팀은 이미 깃허브 추적 설정이 완료된 상태예요.", HttpStatus.CONFLICT),
	GITHUB_API_ERROR("GITHUB500", "Github API 호출에 실패했어요.", HttpStatus.PROCESSING),
	GITHUB_CONFIG_NOT_FOUND("GITHUB404", "Github 설정이 존재하지 않아요.", HttpStatus.NOT_FOUND),

	SHA_INTERNAL_ERROR("SHA500", "SHA 정보를 불러올 수 없어요.", HttpStatus.INTERNAL_SERVER_ERROR);


	private final String errorCode;
	private final String message;
	private final HttpStatus status;
}
