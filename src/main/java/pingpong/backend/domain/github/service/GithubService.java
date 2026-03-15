package pingpong.backend.domain.github.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.github.Github;
import pingpong.backend.domain.github.GithubErrorCode;
import pingpong.backend.domain.github.client.GithubClient;
import pingpong.backend.domain.github.dto.request.GithubConfigRequest;
import pingpong.backend.domain.github.dto.response.BranchListResponse;
import pingpong.backend.domain.github.dto.response.BranchResponse;
import pingpong.backend.domain.github.dto.response.GithubSyncResult;
import pingpong.backend.domain.github.repository.GithubRepository;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.TeamErrorCode;
import pingpong.backend.domain.team.repository.TeamRepository;
import pingpong.backend.global.exception.CustomException;

@Service
@Slf4j
@RequiredArgsConstructor
public class GithubService {

	private final TeamRepository teamRepository;
	private final GithubClient githubClient;
	private final GithubRepository githubRepository;

	@Transactional(readOnly = true)
	public BranchListResponse getAllBranches(Long teamId,String owner,String repo) {
		if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
			throw new CustomException(GithubErrorCode.REPOSITORY_NOT_FOUND);
		}

		teamRepository.findById(teamId).orElseThrow(()->new CustomException(TeamErrorCode.TEAM_NOT_FOUND));
		List<BranchResponse> githubBranches=githubClient.fetchBranches(owner,repo);

		if(githubBranches.isEmpty()) {
			return new BranchListResponse(List.of());
		}

		List<BranchListResponse.BranchItem> items=githubBranches.stream()
			.map(b->new BranchListResponse.BranchItem(b.name(),b.commit().sha()))
			.toList();
		return new BranchListResponse(items);
	}

	@Transactional
	public void configGithub(Long teamId, GithubConfigRequest request){
		if(githubRepository.existsByTeamId(teamId)){
			throw new CustomException(GithubErrorCode.GITHUB_CONFIG_CONFLICT);
		}
		boolean isValid=githubClient.validateBranch(request.repoOwner(),request.repoName(),request.branch());
		if(!isValid) {
			throw new CustomException(GithubErrorCode.REPOSITORY_NOT_FOUND);
		}

		Team team=teamRepository.findById(teamId).orElseThrow(()->new CustomException(TeamErrorCode.TEAM_NOT_FOUND));
		Github github=Github.builder()
			.team(team)
			.repoOwner(request.repoOwner())
			.repoName(request.repoName())
			.branch(request.branch())
			.lastHeadSha(null)
			.build();

		githubRepository.save(github);
	}

	public GithubSyncResult syncGithubBranch(Long teamId){
		Github github=githubRepository.findById(teamId).orElseThrow(
			()->new CustomException(GithubErrorCode.GITHUB_CONFIG_NOT_FOUND)
		);
		String newHeadSha=githubClient.getLatestHeadSha(
			github.getRepoOwner(),
			github.getRepoName(),
			github.getBranch()
		);

		String lastHeadSha=github.getLastHeadSha();
		//첫 sync
		if(lastHeadSha==null){
			github.updateSyncInfo(newHeadSha);
			return GithubSyncResult.noChange();
		}
		//변경 없음
		if(lastHeadSha.equals(newHeadSha)){
			return GithubSyncResult.noChange();
		}
		//변경 있음. Diff 로직 실행
		Object diffData=githubClient.compareCommits(
			github.getRepoOwner(),
			github.getRepoName(),
			lastHeadSha,
			newHeadSha
		);

		github.updateSyncInfo(newHeadSha);

	}
}
