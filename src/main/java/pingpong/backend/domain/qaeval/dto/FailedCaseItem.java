package pingpong.backend.domain.qaeval.dto;

public record FailedCaseItem(
	Long qaCaseId,
	String scenarioName,
	String testType,
	Long endpointId,
	String endpointPath,
	String endpointMethod
) {
}
