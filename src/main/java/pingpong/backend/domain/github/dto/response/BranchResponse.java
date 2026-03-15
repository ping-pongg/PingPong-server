package pingpong.backend.domain.github.dto.response;

public record BranchResponse (
	String name,
	Commit commit
){
	public record Commit(String sha){}
}
