package pingpong.backend.domain.github.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.github.GithubErrorCode;
import pingpong.backend.domain.github.dto.response.BranchResponse;
import pingpong.backend.domain.github.dto.response.GithubRefResponse;
import pingpong.backend.domain.github.dto.response.GithubSyncDetailResponse;
import pingpong.backend.global.exception.CustomException;

@Component
@Slf4j
public class GithubClient {

	private final RestTemplate restTemplate;
	public GithubClient(@Qualifier("githubRestTemplate") RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	public List<BranchResponse> fetchBranches(String owner,String repo){
		String url=String.format("https://api.github.com/repos/%s/%s/branches",owner,repo);

		BranchResponse[] response=restTemplate.getForObject(url,BranchResponse[].class);
		return response!=null? Arrays.asList(response):List.of();

	}

	public String getLatestHeadSha(String owner,String repo,String branch){
		String url=String.format("https://api.github.com/repos/%s/%s/git/ref/heads/%s",owner,repo,branch);

		try{
			GithubRefResponse response=restTemplate.getForObject(url,GithubRefResponse.class);
			if(response!=null && response.object()!=null){
				return response.object().sha();
			}
			throw new CustomException(GithubErrorCode.SHA_INTERNAL_ERROR);
		}catch(HttpClientErrorException.NotFound e){
			throw new CustomException(GithubErrorCode.REPOSITORY_NOT_FOUND);
		}catch(Exception e){
			throw new CustomException(GithubErrorCode.GITHUB_API_ERROR);
		}
	}

	public boolean validateBranch(String owner,String repo,String branchName){
		try{
			List<BranchResponse> branches=fetchBranches(owner,repo);
			return branches.stream()
				.anyMatch(b->b.name().equals(branchName));
		}catch(Exception e){
			return false;
		}
	}

	public GithubSyncDetailResponse compareCommits(String repoOwner,String repoName,String base,String head){
		String url=String.format("https://api.github.com/repos/%s/%s/compare/%s...%s",repoOwner,repoName,base,head);

		try {
			ResponseEntity<Map> responseEntity = restTemplate.getForEntity(url, Map.class);
			Map<String, Object> body = responseEntity.getBody();
			if (body == null)
				throw new CustomException(GithubErrorCode.GITHUB_API_EMPTY_ERROR);

			//최신 커밋 정보 추출 (리스트의 마지막 요소)
			List<Map<String, Object>> commits = (List<Map<String, Object>>)body.get("commits");
			Map<String, Object> lastCommitMap = commits.get(commits.size() - 1);
			Map<String, Object> commitDetail = (Map<String, Object>)lastCommitMap.get("commit");
			Map<String, Object> authorDetail = (Map<String, Object>)lastCommitMap.get("author");

			var latestCommit = new GithubSyncDetailResponse.LatestCommit(
				(String)commitDetail.get("message"),
				(String)authorDetail.get("login"),
				(String)authorDetail.get("avatar_url"),
				(String)((Map<String, Object>)commitDetail.get("authorr")).get("date")
			);

			//파일 목록 및 패치 파싱
			List<Map<String, Object>> files = (List<Map<String, Object>>)body.get("files");
			int totalAdd = 0, totalDel = 0;
			List<GithubSyncDetailResponse.FileInfo> fileInfos = new ArrayList<>();

			for (Map<String, Object> file : files) {
				int add = (int)file.get("additions");
				int del = (int)file.get("deletions");
				totalAdd += add;
				totalDel += del;

				fileInfos.add(new GithubSyncDetailResponse.FileInfo(
					(String)file.get("filename"),
					(String)file.get("blob_url"),
					(String)file.get("status"),
					add, del, (int)file.get("changes"),
					parsePatch((String)file.get("patch"))
				));
			}
			return new GithubSyncDetailResponse(
				new GithubSyncDetailResponse.CompareInfo((String) body.get("html_url"),latestCommit),
				new GithubSyncDetailResponse.SummaryInfo(files.size(),totalAdd,totalDel,totalAdd+totalDel),
				fileInfos
			);
		}catch(Exception e){
			log.error("Diff 분석 실패:",e);
			throw new CustomException(GithubErrorCode.GITHUB_DIFF_ERROR);
		}
	}

	private List<GithubSyncDetailResponse.ChangeLine> parsePatch(String patch){
		if(patch==null) return List.of();

		List<GithubSyncDetailResponse.ChangeLine> lines=new ArrayList<>();
		String[] splitPatch=patch.split("\n");

		for(String line:splitPatch){
			if(line.startsWith("@@")) continue;

			if(line.startsWith("+")){
				lines.add(new GithubSyncDetailResponse.ChangeLine("add",line.substring(1)));
			}else if(line.startsWith("-")){
				lines.add(new GithubSyncDetailResponse.ChangeLine("delete",line.substring(1)));
			}else{
				lines.add(new GithubSyncDetailResponse.ChangeLine("unchanged",line.length()>0?line.substring(1):""));
			}
		}
		return lines;
	}


}
