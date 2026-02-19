package pingpong.backend.domain.swagger.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import pingpong.backend.domain.swagger.SwaggerResponse;

public record ResponseSnapshot(
	String statusCode,
	String mediaType,
	String description
) {

	public static ResponseSnapshot from(
		SwaggerResponse r,
		ObjectMapper mapper
	) {
		if (r == null) return null;

		return new ResponseSnapshot(
			r.getStatusCode(),
			r.getMediaType(),
			r.getDescription()
		);
	}
}

