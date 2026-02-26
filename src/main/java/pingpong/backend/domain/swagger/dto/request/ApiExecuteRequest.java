package pingpong.backend.domain.swagger.dto.request;

import java.util.Map;

public record ApiExecuteRequest(
	Map<String, String> pathVariables,
	Map<String, String> queryParams,
	Map<String, String> headers,
	Object body
) {}
