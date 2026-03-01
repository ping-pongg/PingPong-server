package pingpong.backend.domain.swagger.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pingpong.backend.domain.swagger.SwaggerResponse;

public record SnapshotResponse(
	String statusCode,
	String mediaType,
	String description,
	JsonNode schema
) {

	public static SnapshotResponse from(
		SwaggerResponse r,
		ObjectMapper mapper
	) {
		if (r == null) return null;

		JsonNode schemaNode = null;

		try {
			if (r.getSchemaJson() != null) {
				schemaNode = mapper.readTree(r.getSchemaJson());
			}
		} catch (Exception e) {
			throw new RuntimeException("Response schema parsing 실패", e);
		}

		return new SnapshotResponse(
			r.getStatusCode(),
			r.getMediaType(),
			r.getDescription(),
			schemaNode
		);
	}
}