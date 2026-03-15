package pingpong.backend.domain.github.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.github.dto.request.GithubConfigRequest;
import pingpong.backend.domain.github.dto.response.BranchListResponse;
import pingpong.backend.domain.github.dto.response.GithubConfigResponse;
import pingpong.backend.domain.github.dto.response.GithubSyncResult;
import pingpong.backend.domain.github.service.GithubService;
import pingpong.backend.domain.github.service.GithubUrlParser;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Github API", description = "Github branch의 코드 Diff를 계산 및 조회하는 API입니다.")
public class GithubController {

	private final GithubService githubService;

	@GetMapping("/teams/github/branches")
	@Operation(summary = "owner/repo에 해당하는 브랜치 목록 조회")
	public SuccessResponse<BranchListResponse> getBranchList(
		@RequestParam @Schema(description="Github 레포 URL",example="https://github.com/Nexus-team-02/Nexus-server") String url
	){
		GithubUrlParser.RepoInfo repoInfo= GithubUrlParser.parse(url);
		return SuccessResponse.ok(githubService.getAllBranches(repoInfo.owner(),repoInfo.repo()));
	}

	@Hidden
	@PostMapping("/teams/{teamId}/github/config")
	@Operation(summary = "추적할 repository, branch 설정")
	public SuccessResponse<Void> configGithub(
		@PathVariable Long teamId,
		@RequestBody @Valid GithubConfigRequest request
	){

		githubService.configGithub(teamId,request);
		return SuccessResponse.ok();
	}

	@PutMapping("/teams/{teamId}/github/config")
	@Operation(summary="추적할 repository, branch 변경")
	public SuccessResponse<Void> changeConfigGithub(
		@PathVariable Long teamId,
		@RequestBody @Valid GithubConfigRequest request
	){
		githubService.changeConfigGithub(teamId,request);
		return SuccessResponse.ok();
	}

	@GetMapping("/teams/{teamId}/github/config")
	@Operation(summary="현재 추적 설정 조회")
	public SuccessResponse<GithubConfigResponse> getGithubConfig(
		@PathVariable Long teamId
	){
		return SuccessResponse.ok(githubService.getGithubConfig(teamId));
	}

	@DeleteMapping("/teams/{teamId}/github/config")
	@Operation(summary="추적 설정 삭제")
	public SuccessResponse<Void> deleteGithubConfig(
		@PathVariable Long teamId
	){
		githubService.deleteGithubConfig(teamId);
		return SuccessResponse.ok();
	}

	@PostMapping("/teams/{teamId}/github/sync")
	@Operation(summary="최신 커밋을 동기화 실행 후 diff 반환")
	public SuccessResponse<GithubSyncResult> githubSync(
		@PathVariable Long teamId
	){
		return SuccessResponse.ok(githubService.syncGithubBranch(teamId));
	}
}
