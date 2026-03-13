package pingpong.backend.domain.qa.dto;

import java.util.List;
import java.util.Map;

public record QaTemplateResponse(
	Long endpointId,
	String path,
	String method,
	String summary,
	List<ParameterTemplate> parameters,
	String requestBodyTemplate, // 기본 JSON 구조
	Map<String, String> defaultHeaders
) {}

