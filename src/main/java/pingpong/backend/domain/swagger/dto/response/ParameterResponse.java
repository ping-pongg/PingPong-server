package pingpong.backend.domain.swagger.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;

import pingpong.backend.domain.swagger.SwaggerParameter;
import pingpong.backend.domain.swagger.enums.DiffType;

public record ParameterResponse (
	DiffType diffType,
	ParameterSnapshotResponse before,
	ParameterSnapshotResponse after
){

	public static ParameterResponse of(
		SwaggerParameter prev,
		SwaggerParameter curr,
		DiffType diff,
		ObjectMapper mapper
	) {
		return new ParameterResponse(
			diff,
			ParameterSnapshotResponse.from(prev, mapper),
			ParameterSnapshotResponse.from(curr, mapper)
		);
	}
}
