package pingpong.backend.domain.swagger.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import pingpong.backend.domain.swagger.SwaggerParameter;
import pingpong.backend.domain.swagger.enums.DiffType;

public record ParameterResponse (
	DiffType diffType,
	ParameterSnapshotRes before,
	ParameterSnapshotRes after
){

	public static ParameterResponse of(
		SwaggerParameter prev,
		SwaggerParameter curr,
		DiffType diff,
		ObjectMapper mapper
	) {
		return new ParameterResponse(
			diff,
			ParameterSnapshotRes.from(prev, mapper),
			ParameterSnapshotRes.from(curr, mapper)
		);
	}
}
