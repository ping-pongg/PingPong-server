package pingpong.backend.domain.swagger.dto.response;

public record SyncResultResponse(
	boolean swaggerChanged,
	boolean githubChanged
) {
	public static SyncResultResponse of(boolean swaggerChanged, boolean githubChanged) {
		return new SyncResultResponse(swaggerChanged, githubChanged);
	}
}