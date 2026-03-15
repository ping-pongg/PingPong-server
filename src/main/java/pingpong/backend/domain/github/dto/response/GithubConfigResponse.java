package pingpong.backend.domain.github.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record GithubConfigResponse (
	String repoOwner,
	String repoName,
	String branch,
	String lastHeadSha,
	LocalDateTime lastSyncedAt
){
}
