package pingpong.backend.domain.swagger.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import pingpong.backend.domain.swagger.SwaggerRequest;

public record RequestSnapshot(
	String mediaType,
	Boolean required
) {

	public static RequestSnapshot from(
		SwaggerRequest r,
		ObjectMapper mapper
	) {
		if (r == null) return null;

		return new RequestSnapshot(
			r.getMediaType(),
			r.isRequired()
		);
	}
}

