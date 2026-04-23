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
import pingpong.backend.domain.github.dto.response.GithubConfigResponse;
import pingpong.backend.domain.github.dto.response.GithubSyncDetailResponse;
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
	public BranchListResponse getAllBranches(String owner,String repo) {
		if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
			throw new CustomException(GithubErrorCode.REPOSITORY_NOT_FOUND);
		}
		List<BranchResponse> githubBranches=githubClient.fetchBranches(owner,repo);

		if(githubBranches.isEmpty()) {
			return new BranchListResponse(List.of());
		}

		List<BranchListResponse.BranchItem> items=githubBranches.stream()
			.map(b->new BranchListResponse.BranchItem(b.name(),b.commit().sha()))
			.toList();
		return new BranchListResponse(items);
	}
	//더미더미

	@Transactional
	public void configGithub(Long teamId, GithubConfigRequest request){
		if(githubRepository.existsByTeamId(teamId)){
			throw new CustomException(GithubErrorCode.GITHUB_CONFIG_CONFLICT);
		}
		GithubUrlParser.RepoInfo repoInfo= GithubUrlParser.parse(request.url());
		boolean isValid=githubClient.validateBranch(repoInfo.owner(), repoInfo.repo(),request.branch());
		if(!isValid) {
			throw new CustomException(GithubErrorCode.REPOSITORY_NOT_FOUND);
		}

		Team team=teamRepository.findById(teamId).orElseThrow(()->new CustomException(TeamErrorCode.TEAM_NOT_FOUND));
		Github github=Github.builder()
			.team(team)
			.repoOwner(repoInfo.owner())
			.repoName(repoInfo.repo())
			.branch(request.branch())
			.lastHeadSha(null)
			.newHeadSha(null)
			.build();

		githubRepository.save(github);
	}

	@Transactional
	public void changeConfigGithub(Long teamId, GithubConfigRequest request){
		Github github = githubRepository.findByTeamId(teamId)
			.orElseThrow(() -> new CustomException(GithubErrorCode.GITHUB_CONFIG_NOT_FOUND));
		Team team=github.getTeam();

		boolean isUrlChanged = !request.url().equals(team.getGithub());

		if(isUrlChanged) {
			GithubUrlParser.RepoInfo repoInfo = GithubUrlParser.parse(request.url());
			boolean isValid = githubClient.validateBranch(repoInfo.owner(), repoInfo.repo(), request.branch());

			if (!isValid) {
				throw new CustomException(GithubErrorCode.REPOSITORY_NOT_FOUND);
			}

			String lastHeadSha=githubClient.getLatestHeadSha(repoInfo.owner(), repoInfo.repo(), request.branch());
			github.updateConfig(repoInfo.owner(), repoInfo.repo(), request.branch(),lastHeadSha);
			team.updateGithub(request.url());
		}else{
			boolean isValid = githubClient.validateBranch(github.getRepoOwner(), github.getRepoName(), request.branch());
			if (!isValid) {
				throw new CustomException(GithubErrorCode.REPOSITORY_NOT_FOUND);
			}
			String lastHeadSha=githubClient.getLatestHeadSha(github.getRepoOwner(), github.getRepoName(), request.branch());
			github.updateConfig(github.getRepoOwner(), github.getRepoName(), request.branch(),lastHeadSha);
		}
	}


	@Transactional
	public void deleteGithubConfig(Long teamId){
		Github github = githubRepository.findByTeamId(teamId)
			.orElseThrow(() -> new CustomException(GithubErrorCode.GITHUB_CONFIG_NOT_FOUND));
		Team team=github.getTeam();
		team.updateGithub(null);
		githubRepository.delete(github);
	}

	@Transactional(readOnly = true)
	public GithubConfigResponse getGithubConfig(Long teamId){
		Github github = githubRepository.findByTeamId(teamId)
			.orElseThrow(() -> new CustomException(GithubErrorCode.GITHUB_CONFIG_NOT_FOUND));

		return GithubConfigResponse.builder()
			.repoOwner(github.getRepoOwner())
			.repoName(github.getRepoName())
			.branch(github.getBranch())
			.lastHeadSha((github.getLastHeadSha()==null||github.getLastHeadSha().isBlank())?null:github.getLastHeadSha())
			.newHeadSha((github.getNewHeadSha()==null||github.getNewHeadSha().isBlank())?null:github.getNewHeadSha())
			.lastSyncedAt((github.getLastSyncedAt()==null?null:github.getLastSyncedAt()))
			.build();
	}

	@Transactional
	public boolean syncGithubBranch(Long teamId){
		Github github=githubRepository.findByTeamId(teamId).orElseThrow(
			()->new CustomException(GithubErrorCode.GITHUB_CONFIG_NOT_FOUND)
		);
		String currentRemoteSha = githubClient.getLatestHeadSha(
			github.getRepoOwner(), github.getRepoName(), github.getBranch()
		);

		String baseSha = github.getNewHeadSha();

		if (baseSha == null) {
			github.updateSyncInfo(currentRemoteSha, currentRemoteSha);
			return false;
		}

		if (baseSha.equals(currentRemoteSha)) {
			log.info("github code 변경사항이 없어요.");
			return false; // 원격 데이터가 이전 동기화 때와 같음
		}

		// 3. 변경 사항이 있는 경우 (SHA가 다름)
		log.info("github code 변경사항이 있어요.");
		github.updateSyncInfo(baseSha,currentRemoteSha);
		return true;
	}

	@Transactional(readOnly = true)
	public GithubSyncResult getLatestGithubDiff(Long teamId){
		Github github=githubRepository.findByTeamId(teamId).orElseThrow(()-> new CustomException(GithubErrorCode.GITHUB_CONFIG_NOT_FOUND));

		if (github.getLastHeadSha() == null) {
			return GithubSyncResult.noChange();
		}
		GithubSyncDetailResponse diffData=githubClient.compareCommits(
			github.getRepoOwner(),
			github.getRepoName(),
			github.getLastHeadSha(),
			github.getNewHeadSha()
		);
		return new GithubSyncResult(true,diffData);
	}
}
