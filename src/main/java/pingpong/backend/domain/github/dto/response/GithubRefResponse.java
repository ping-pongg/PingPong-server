package pingpong.backend.domain.github.dto.response;

public record GithubRefResponse (
	String ref,
	String url,
	GitObject object
){
	public record GitObject (
		String sha,
		String type,
		String url
	){}
}
