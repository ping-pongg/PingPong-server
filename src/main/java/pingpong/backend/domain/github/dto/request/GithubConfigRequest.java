package pingpong.backend.domain.github.dto.request;

import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Github 추적 설정 요청")
public record GithubConfigRequest (

	@Schema(description="Github 레포 URL",example="https://github.com/Nexus-team-02/Nexus-server")
	String url,

	@Schema(description = "추적할 브랜치(미입력 시 main)",example="main",defaultValue = "main")
	String branch
){
}
