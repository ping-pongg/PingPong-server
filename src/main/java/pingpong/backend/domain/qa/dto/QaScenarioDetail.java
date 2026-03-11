package pingpong.backend.domain.qa.dto;

public record QaScenarioDetail(
	String scenarioName,
	String testType,
	String description,
	RequestData requestData,
	ExpectedResponse expectedResponse,
	String executionCodeSnippet
) {}
