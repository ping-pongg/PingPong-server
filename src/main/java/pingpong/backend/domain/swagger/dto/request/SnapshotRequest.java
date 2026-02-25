package pingpong.backend.domain.swagger.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;

import pingpong.backend.domain.swagger.SwaggerRequest;

public record SnapshotRequest(
	String mediaType,
	Boolean required
) {

	public static SnapshotRequest from(
		SwaggerRequest r,
		ObjectMapper mapper
	) {
		if (r == null) return null;

		return new SnapshotRequest(
			r.getMediaType(),
			r.isRequired()
		);
	}
}

