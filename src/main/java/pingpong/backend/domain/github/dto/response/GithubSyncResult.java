package pingpong.backend.domain.github.dto.response;

public record GithubSyncResult (
	boolean changed,
	Object data
){
	public static GithubSyncResult noChange(){
		return new GithubSyncResult(false, null);
	}
}
