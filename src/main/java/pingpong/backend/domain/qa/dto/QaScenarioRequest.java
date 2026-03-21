package pingpong.backend.domain.qa.dto;

import pingpong.backend.domain.qa.enums.TestType;

public record QaScenarioRequest (
	String scenarioName,
	TestType testType,
	String description,
	QaCaseDetailDto.QaData qaData
){
}
