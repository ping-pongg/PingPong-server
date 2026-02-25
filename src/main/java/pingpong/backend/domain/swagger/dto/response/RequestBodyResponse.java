package pingpong.backend.domain.swagger.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;

import pingpong.backend.domain.swagger.SwaggerRequest;
import pingpong.backend.domain.swagger.dto.request.SnapshotRequest;
import pingpong.backend.domain.swagger.enums.DiffType;

public record RequestBodyResponse(
	DiffType diffType,
	SnapshotRequest before,
	SnapshotRequest after
) {

	public static RequestBodyResponse of(
		SwaggerRequest prev,
		SwaggerRequest curr,
		DiffType diff,
		ObjectMapper mapper
	) {
		return new RequestBodyResponse(
			diff,
			SnapshotRequest.from(prev, mapper),
			SnapshotRequest.from(curr, mapper)
		);
	}
}

