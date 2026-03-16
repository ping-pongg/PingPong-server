package pingpong.backend.domain.qa.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.SwaggerRequest;

@Schema(description = "엔드포인트 요청 본문 정보")
public record EndpointRequestBodyDto(

	@Schema(description = "미디어 타입 (예: application/json)")
	String mediaType,

	@Schema(description = "필수 여부")
	Boolean required,

	@Schema(description = "요청 본문 스키마")
	JsonNode schema
) {

	public static EndpointRequestBodyDto fromEntity(SwaggerRequest r, ObjectMapper mapper) {
		if (r == null) return null;
		JsonNode schemaNode = null;
		try {
			if (r.getSchemaJson() != null) {
				schemaNode = mapper.readTree(r.getSchemaJson());
			}
		} catch (Exception ignored) {
		}
		return new EndpointRequestBodyDto(
			r.getMediaType(),
			r.isRequired(),
			schemaNode
		);
	}
}
