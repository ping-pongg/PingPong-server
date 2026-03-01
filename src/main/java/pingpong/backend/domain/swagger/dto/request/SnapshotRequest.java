package pingpong.backend.domain.swagger.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pingpong.backend.domain.swagger.SwaggerRequest;

public record SnapshotRequest(
	String mediaType,
	Boolean required,
	JsonNode schema
) {

	public static SnapshotRequest from(
		SwaggerRequest r,
		ObjectMapper mapper
	) {
		if (r == null) return null;

		JsonNode schemaNode = null;

		try {
			if (r.getSchemaJson() != null) {
				schemaNode = mapper.readTree(r.getSchemaJson());
			}
		} catch (Exception e) {
			throw new RuntimeException("Schema parsing 실패", e);
		}

		return new SnapshotRequest(
			r.getMediaType(),
			r.isRequired(),
			schemaNode
		);
	}
}

