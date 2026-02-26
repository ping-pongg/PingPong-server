package pingpong.backend.domain.swagger.dto.response;

import java.util.Map;

public record ApiExecuteResponse(
	int httpStatus,
	Map<String, String> responseHeaders,
	Object body
) {}
