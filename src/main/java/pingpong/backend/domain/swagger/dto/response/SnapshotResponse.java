package pingpong.backend.domain.swagger.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;

import pingpong.backend.domain.swagger.SwaggerResponse;

public record SnapshotResponse(
	String statusCode,
	String mediaType,
	String description
) {

	public static SnapshotResponse from(
		SwaggerResponse r,
		ObjectMapper mapper
	) {
		if (r == null) return null;

		return new SnapshotResponse(
			r.getStatusCode(),
			r.getMediaType(),
			r.getDescription()
		);
	}
}

