package pingpong.backend.domain.flow.dto.response;

public record FlowListItemResponse(
	Long flowId,
	String title,
	String description,
	String thumbnailUrl,
	Boolean alert
) {}
