package pingpong.backend.domain.swagger.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;

import pingpong.backend.domain.swagger.SwaggerResponse;
import pingpong.backend.domain.swagger.enums.DiffType;

public record ResponseBodyResponse(
	DiffType diffType,
	SnapshotResponse before,
	SnapshotResponse after
) {

	public static ResponseBodyResponse of(
		SwaggerResponse prev,
		SwaggerResponse curr,
		DiffType diff,
		ObjectMapper mapper
	) {
		return new ResponseBodyResponse(
			diff,
			SnapshotResponse.from(prev, mapper),
			SnapshotResponse.from(curr, mapper)
		);
	}
}


