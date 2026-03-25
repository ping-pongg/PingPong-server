package pingpong.backend.domain.qa.dto;

public record QaPathVariableResponse(
	String paramName,
	String schemaType,
	String currentValue
) {
}
