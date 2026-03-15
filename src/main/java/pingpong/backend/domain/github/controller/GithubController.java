package pingpong.backend.domain.github.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import pingpong.backend.domain.github.dto.request.GithubConfigRequest;
import pingpong.backend.domain.github.dto.response.BranchListResponse;
import pingpong.backend.domain.github.dto.response.GithubSyncResult;
import pingpong.backend.domain.github.service.GithubService;
import pingpong.backend.global.response.result.SuccessResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Github API", description = "Endpoint에 대한 QA 케이스를 조회하는 API입니다.")
public class GithubController {

	private final GithubService githubService;

	@GetMapping("/teams/{teamId}/github/branches")
	@Operation(summary = "owner/repo에 해당하는 브랜치 목록 조회")
	public SuccessResponse<BranchListResponse> getBranchList(
		@PathVariable Long teamId,
		@RequestParam String owner,
		@RequestParam String repo
	){
		return SuccessResponse.ok(githubService.getAllBranches(teamId,owner,repo));
	}

	@PostMapping("/teams/{teamId}/github/config")
	@Operation(summary = "추적할 repository, branch 설정")
	public SuccessResponse<Void> getBranchList(
		@PathVariable Long teamId,
		@RequestBody GithubConfigRequest request
	){
		githubService.configGithub(teamId,request);
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
