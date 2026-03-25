package pingpong.backend.domain.qa.dto;

public record QaPathVariableResponse(
	Long id,
	String paramName,
	String schemaType,
	String currentValue
) {
}
