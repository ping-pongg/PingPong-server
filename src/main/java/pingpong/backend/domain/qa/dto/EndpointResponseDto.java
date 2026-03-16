package pingpong.backend.domain.qa.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.SwaggerResponse;

@Schema(description = "엔드포인트 응답 정보")
public record EndpointResponseDto(

	@Schema(description = "HTTP 상태 코드")
	String statusCode,

	@Schema(description = "미디어 타입")
	String mediaType,

	@Schema(description = "응답 설명")
	String description,

	@Schema(description = "응답 본문 스키마")
	JsonNode schema
) {

	public static EndpointResponseDto fromEntity(SwaggerResponse r, ObjectMapper mapper) {
		if (r == null) return null;
		JsonNode schemaNode = null;
		try {
			if (r.getSchemaJson() != null) {
				schemaNode = mapper.readTree(r.getSchemaJson());
			}
		} catch (Exception ignored) {
		}
		return new EndpointResponseDto(
			r.getStatusCode(),
			r.getMediaType(),
			r.getDescription(),
			schemaNode
		);
	}
}
