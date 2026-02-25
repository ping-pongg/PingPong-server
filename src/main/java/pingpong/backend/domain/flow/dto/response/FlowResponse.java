package pingpong.backend.domain.flow.dto.response;

import java.util.List;

public record FlowResponse(
	Long flowId,
	String title,
	String description,
	List<FlowImageResponse> images
) {}

