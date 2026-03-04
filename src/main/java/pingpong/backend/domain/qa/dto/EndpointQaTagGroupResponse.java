package pingpong.backend.domain.qa.dto;

import java.util.List;

public record EndpointQaTagGroupResponse(
	String tag,
	List<EndpointQaSummaryResponse> endpoints
) {}
