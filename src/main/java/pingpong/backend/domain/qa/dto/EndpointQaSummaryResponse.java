package pingpong.backend.domain.qa.dto;

import pingpong.backend.domain.swagger.enums.CrudMethod;

public record EndpointQaSummaryResponse(
	Long endpointId,
	CrudMethod method,
	String path,
	Double successRate
) {}
