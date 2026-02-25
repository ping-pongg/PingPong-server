package pingpong.backend.domain.swagger.dto.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.SwaggerParameter;
import pingpong.backend.global.exception.CustomException;

@Schema(description = "parameter diff 응답")
public record ParameterSnapshotResponse(

	String name,
	
	@Schema(description = "파라미터 위치: query,path,header,cookie")
	String in,
	
	@Schema(description = "데이터 타입")
	String type,

	Boolean required,
	
	@Schema(description = "파라미터 설명")
	String description

){
	public static ParameterSnapshotResponse from(
		SwaggerParameter p,
		ObjectMapper mapper
	) {
		// null-safe (REMOVED/ADDED 대응)
		if (p == null) {
			return null;
		}

		JsonNode schemaNode = null;

		try {
			if (p.getSchemaJson() != null) {
				schemaNode = mapper.readTree(p.getSchemaJson());
			}
		} catch (JsonProcessingException e) {
			throw new CustomException(
				SwaggerErrorCode.JSON_PROCESSING_EXCEPTION
			);
		}

		return new ParameterSnapshotResponse(
			p.getName(),
			p.getInType(),
			extractType(schemaNode),
			p.getRequired(),
			p.getDescription()
		);
	}

	private static String extractType(JsonNode schemaNode) {
		if (schemaNode == null || schemaNode.isNull()) {
			return null;
		}

		JsonNode refNode = schemaNode.get("$ref");
		if (refNode != null && !refNode.isNull()) {
			return "ref";
		}

		JsonNode typeNode = schemaNode.get("type");
		if (typeNode == null || typeNode.isNull()) {
			return null;
		}

		String type = typeNode.asText();

		if ("array".equals(type)) {
			JsonNode itemsNode = schemaNode.get("items");
			String innerType = extractType(itemsNode);
			return innerType != null
				? "array<" + innerType + ">"
				: "array";
		}

		return type;
	}


}
