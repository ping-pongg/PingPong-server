package pingpong.backend.domain.swaggerdiff.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.SwaggerParameter;

@Schema(description = "엔드포인트 파라미터 정보")
public record EndpointParameterDto(

	@Schema(description = "파라미터 이름")
	String name,

	@Schema(description = "파라미터 위치 (query, path, header, cookie)")
	String in,

	@Schema(description = "데이터 타입")
	String type,

	@Schema(description = "필수 여부")
	Boolean required,

	@Schema(description = "파라미터 설명")
	String description,

	@Schema(description = "예시 값")
	JsonNode exampleValue
) {

	public static EndpointParameterDto fromEntity(SwaggerParameter p, ObjectMapper mapper) {
		if (p == null) return null;

		String type = null;
		JsonNode exampleValue = null;
		try {
			if (p.getSchemaJson() != null) {
				JsonNode schemaNode = mapper.readTree(p.getSchemaJson());
				type = extractType(schemaNode);
				JsonNode exNode = schemaNode.get("example");
				if (exNode != null && !exNode.isNull()) {
					exampleValue = exNode;
				} else {
					JsonNode typeNode = schemaNode.get("type");
					if (typeNode != null && !typeNode.isNull()) {
						exampleValue = typeNode;
					}
				}
			}
		} catch (JsonProcessingException ignored) {
		}

		return new EndpointParameterDto(
			p.getName(),
			p.getInType(),
			type,
			p.getRequired(),
			p.getDescription(),
			exampleValue
		);
	}

	private static String extractType(JsonNode schemaNode) {
		if (schemaNode == null || schemaNode.isNull()) return null;
		if (schemaNode.has("$ref")) return "ref";
		JsonNode typeNode = schemaNode.get("type");
		if (typeNode == null || typeNode.isNull()) return null;
		String type = typeNode.asText();
		if ("array".equals(type)) {
			String inner = extractType(schemaNode.get("items"));
			return inner != null ? "array<" + inner + ">" : "array";
		}
		return type;
	}
}
