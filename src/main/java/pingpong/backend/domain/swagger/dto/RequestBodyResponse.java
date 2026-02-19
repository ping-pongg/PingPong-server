package pingpong.backend.domain.swagger.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import pingpong.backend.domain.swagger.SwaggerRequest;
import pingpong.backend.domain.swagger.enums.DiffType;

public record RequestBodyResponse(
	DiffType diffType,
	RequestSnapshot before,
	RequestSnapshot after
) {

	public static RequestBodyResponse of(
		SwaggerRequest prev,
		SwaggerRequest curr,
		DiffType diff,
		ObjectMapper mapper
	) {
		return new RequestBodyResponse(
			diff,
			RequestSnapshot.from(prev, mapper),
			RequestSnapshot.from(curr, mapper)
		);
	}
}

