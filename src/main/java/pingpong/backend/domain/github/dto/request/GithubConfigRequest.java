package pingpong.backend.domain.github.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Github 추적 설정 요청")
public record GithubConfigRequest (

	@Schema(description = "레포지토리 소유자",example="facebook")
	@NotBlank
	String repoOwner,

	@Schema(description = "레포지토리 이름",example="react")
	@NotBlank
	String repoName,

	@Schema(description = "추적할 브랜치(미입력 시 main)",example="main",defaultValue = "main")
	String branch
){
}
