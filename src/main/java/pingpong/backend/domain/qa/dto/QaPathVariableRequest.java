package pingpong.backend.domain.qa.dto;

import java.util.List;

public record QaPathVariableRequest(
	List<ParamUpdate> params
) {
	public record ParamUpdate(
		Long id,
		String value
	) {
	}
}
