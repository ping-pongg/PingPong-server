package pingpong.backend.domain.qa.dto;

import java.util.Map;

public record QaPathVariableRequest(
	Map<String, String> pathVariables
) {
}
