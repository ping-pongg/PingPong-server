package pingpong.backend.domain.qa.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record QaCaseResponse(
	Long id,
	Long endpointId,
	Boolean isSuccess,
	String description,
	Map<String, String> pathVariables,
	Map<String, String> queryParams,
	Map<String, String> headers,
	Object body,
	LocalDateTime createdAt
) {}
