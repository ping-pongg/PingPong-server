package pingpong.backend.domain.github.dto.response;
import java.util.List;

public record GithubSyncDetailResponse (
	CompareInfo compare,
	SummaryInfo summary,
	List<FileInfo> files
){
	public record CompareInfo(
		String githubCompareUrl,
		LatestCommit latestCommit
	){}

	public record LatestCommit(
		String message,
		String authorEmail,
		String authorProfileImage,
		String authoredAt
	){}

	public record SummaryInfo(
		int filesChanged,
		int additions,
		int deletions,
		int changes
	){}

	public record FileInfo(
		String fileName,
		String githubFileUrl,
		String status,
		int additions,
		int deletions,
		int changes,
		List<ChangeLine> changesPreview
	){}

	public record ChangeLine(
		String type, //add,delete,unchanged
		String content
	){}
}
