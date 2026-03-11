package pingpong.backend.domain.qa.dto;

import java.util.List;
import java.util.Map;

public record QaScenarioResponse(
	Long endpointId,
	List<QaScenarioDetail> scenarios
) {
	public QaScenarioResponse {
		if (scenarios == null) {
			scenarios = List.of();
		}
	}
}

