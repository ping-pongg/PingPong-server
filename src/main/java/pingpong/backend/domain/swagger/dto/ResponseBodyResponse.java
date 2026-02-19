package pingpong.backend.domain.swagger.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pingpong.backend.domain.swagger.SwaggerResponse;
import pingpong.backend.domain.swagger.enums.DiffType;

public record ResponseBodyResponse(
	DiffType diffType,
	ResponseSnapshot before,
	ResponseSnapshot after
) {

	public static ResponseBodyResponse of(
		SwaggerResponse prev,
		SwaggerResponse curr,
		DiffType diff,
		ObjectMapper mapper
	) {
		return new ResponseBodyResponse(
			diff,
			ResponseSnapshot.from(prev, mapper),
			ResponseSnapshot.from(curr, mapper)
		);
	}
}


