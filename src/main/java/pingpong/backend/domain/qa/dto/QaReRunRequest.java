package pingpong.backend.domain.qa.dto;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public record QaReRunRequest(
	Map<String, String> pathVariables,
	Map<String, String> queryParams,
	Map<String, String> headers,
	JsonNode body
) {}