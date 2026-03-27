package pingpong.backend.domain.qaeval.dto;

import java.util.List;

public record SchemaMismatchItem(
	Long qaCaseId,
	String scenarioName,
	String testType,
	String sourceType,
	Long endpointId,
	String endpointPath,
	String endpointMethod,
	List<String> issues
) {
}
