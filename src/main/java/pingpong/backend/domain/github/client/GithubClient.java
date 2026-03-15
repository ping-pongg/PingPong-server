package pingpong.backend.domain.github.client;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import pingpong.backend.domain.github.GithubErrorCode;
import pingpong.backend.domain.github.dto.response.BranchResponse;
import pingpong.backend.domain.github.dto.response.GithubRefResponse;
import pingpong.backend.global.exception.CustomException;

@Component
public class GithubClient {
	private final RestTemplate restTemplate=new RestTemplate();

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

	public Object compareCommits(String repoOwner,String repoName,String lastSha,String newSha){

	}


}
