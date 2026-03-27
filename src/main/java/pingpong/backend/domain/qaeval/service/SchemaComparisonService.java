package pingpong.backend.domain.qaeval.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pingpong.backend.domain.qa.QaCase;
import pingpong.backend.domain.qa.enums.TestType;
import pingpong.backend.domain.qaeval.dto.SchemaComparisonResult;
import pingpong.backend.domain.qaeval.dto.SchemaMismatchItem;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerParameter;
import pingpong.backend.domain.swagger.SwaggerRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class SchemaComparisonService {

	private final ObjectMapper objectMapper;

	/**
	 * QaCase body vs SwaggerRequest schemaJson 비교 (B-2: type + required + enum)
	 */
	public SchemaComparisonResult compareBody(
		List<QaCase> cases,
		Map<Long, List<SwaggerRequest>> schemaMap,
		Map<Long, Endpoint> endpointMap
	) {
		int positiveMatch = 0, positiveMismatch = 0;
		int negativeMatch = 0, negativeMismatch = 0;
		List<SchemaMismatchItem> mismatches = new ArrayList<>();

		for (QaCase qa : cases) {
			Long endpointId = qa.getEndpoint().getId();
			Endpoint endpoint = endpointMap.get(endpointId);
			List<SwaggerRequest> requests = schemaMap.getOrDefault(endpointId, Collections.emptyList());

			List<String> issues = validateBody(qa, requests);
			boolean isMatch = issues.isEmpty();

			if (qa.getTestType() == TestType.POSITIVE) {
				if (isMatch) positiveMatch++;
				else positiveMismatch++;
			} else {
				if (isMatch) negativeMatch++;
				else negativeMismatch++;
			}

			if (!isMatch && endpoint != null) {
				mismatches.add(new SchemaMismatchItem(
					qa.getId(),
					qa.getScenarioName(),
					qa.getTestType().name(),
					qa.getSourceType() != null ? qa.getSourceType().name() : null,
					endpointId,
					endpoint.getPath(),
					endpoint.getMethod() != null ? endpoint.getMethod().name() : null,
					issues
				));
			}
		}

		int positiveTotal = positiveMatch + positiveMismatch;
		int negativeTotal = negativeMatch + negativeMismatch;

		return new SchemaComparisonResult(
			"body",
			cases.size(),
			positiveTotal, positiveMatch, positiveMismatch,
			positiveTotal > 0 ? (double) positiveMatch / positiveTotal * 100 : 0.0,
			negativeTotal, negativeMatch, negativeMismatch,
			negativeTotal > 0 ? (double) negativeMatch / negativeTotal * 100 : 0.0,
			mismatches
		);
	}

	/**
	 * QaCase queryParams vs SwaggerParameter(inType='query') 비교 (키 + required)
	 */
	public SchemaComparisonResult compareQueryParams(
		List<QaCase> cases,
		Map<Long, List<SwaggerParameter>> paramMap,
		Map<Long, Endpoint> endpointMap
	) {
		int positiveMatch = 0, positiveMismatch = 0;
		int negativeMatch = 0, negativeMismatch = 0;
		List<SchemaMismatchItem> mismatches = new ArrayList<>();

		for (QaCase qa : cases) {
			Long endpointId = qa.getEndpoint().getId();
			Endpoint endpoint = endpointMap.get(endpointId);
			List<SwaggerParameter> params = paramMap.getOrDefault(endpointId, Collections.emptyList());

			List<String> issues = validateQueryParams(qa, params);
			boolean isMatch = issues.isEmpty();

			if (qa.getTestType() == TestType.POSITIVE) {
				if (isMatch) positiveMatch++;
				else positiveMismatch++;
			} else {
				if (isMatch) negativeMatch++;
				else negativeMismatch++;
			}

			if (!isMatch && endpoint != null) {
				mismatches.add(new SchemaMismatchItem(
					qa.getId(),
					qa.getScenarioName(),
					qa.getTestType().name(),
					qa.getSourceType() != null ? qa.getSourceType().name() : null,
					endpointId,
					endpoint.getPath(),
					endpoint.getMethod() != null ? endpoint.getMethod().name() : null,
					issues
				));
			}
		}

		int positiveTotal = positiveMatch + positiveMismatch;
		int negativeTotal = negativeMatch + negativeMismatch;

		return new SchemaComparisonResult(
			"queryParams",
			cases.size(),
			positiveTotal, positiveMatch, positiveMismatch,
			positiveTotal > 0 ? (double) positiveMatch / positiveTotal * 100 : 0.0,
			negativeTotal, negativeMatch, negativeMismatch,
			negativeTotal > 0 ? (double) negativeMatch / negativeTotal * 100 : 0.0,
			mismatches
		);
	}

	// ── Body 검증 ──

	private List<String> validateBody(QaCase qa, List<SwaggerRequest> requests) {
		JsonNode body = qa.getBody();
		boolean bodyEmpty = (body == null || body.isNull() || body.isEmpty());

		if (requests.isEmpty()) {
			return bodyEmpty ? Collections.emptyList() : List.of("body가 존재하지만 Swagger에 request body 스키마가 정의되어 있지 않음");
		}

		// 첫 번째 SwaggerRequest의 schemaJson 사용 (일반적으로 application/json)
		SwaggerRequest primaryRequest = requests.get(0);
		String schemaJsonStr = primaryRequest.getSchemaJson();

		if (schemaJsonStr == null || schemaJsonStr.isBlank()) {
			return bodyEmpty ? Collections.emptyList() : List.of("Swagger request body 스키마가 비어있음");
		}

		JsonNode schema;
		try {
			schema = objectMapper.readTree(schemaJsonStr);
		} catch (Exception e) {
			log.warn("스키마 JSON 파싱 실패 (endpointId={}): {}", qa.getEndpoint().getId(), e.getMessage());
			return Collections.emptyList();
		}

		if (bodyEmpty) {
			// 스키마가 있는데 body가 없는 경우 → required 필드 확인
			JsonNode requiredNode = schema.get("required");
			if (requiredNode != null && requiredNode.isArray() && !requiredNode.isEmpty()) {
				return List.of("body가 비어있지만 required 필드가 존재: " + requiredNode);
			}
			return Collections.emptyList();
		}

		if (!body.isObject()) {
			String schemaType = schema.has("type") ? schema.get("type").asText() : "object";
			if (!"object".equals(schemaType)) {
				return validateTypeMatch(body, schemaType, "body");
			}
			return List.of("body가 object가 아님 (실제: " + body.getNodeType() + ")");
		}

		return validateObjectAgainstSchema(body, schema);
	}

	private List<String> validateObjectAgainstSchema(JsonNode body, JsonNode schema) {
		List<String> issues = new ArrayList<>();

		JsonNode propertiesNode = schema.get("properties");
		if (propertiesNode == null || !propertiesNode.isObject()) {
			return issues;
		}

		// 스키마에 정의된 키 수집
		Set<String> schemaKeys = new HashSet<>();
		propertiesNode.fieldNames().forEachRemaining(schemaKeys::add);

		// body 키 수집
		Set<String> bodyKeys = new HashSet<>();
		body.fieldNames().forEachRemaining(bodyKeys::add);

		// 1. 미정의 키 확인 (body에 있지만 스키마에 없는 키)
		for (String key : bodyKeys) {
			if (!schemaKeys.contains(key)) {
				issues.add("미정의 필드: " + key);
			}
		}

		// 2. Required 검증
		JsonNode requiredNode = schema.get("required");
		if (requiredNode != null && requiredNode.isArray()) {
			for (JsonNode reqField : requiredNode) {
				String fieldName = reqField.asText();
				if (!bodyKeys.contains(fieldName)) {
					issues.add("required 필드 누락: " + fieldName);
				}
			}
		}

		// 3. Type 검증 + 4. Enum 검증
		for (String key : bodyKeys) {
			if (!schemaKeys.contains(key)) continue;

			JsonNode propSchema = propertiesNode.get(key);
			JsonNode value = body.get(key);

			if (value == null || value.isNull()) continue;

			// Type 검증
			if (propSchema.has("type")) {
				String expectedType = propSchema.get("type").asText();
				List<String> typeIssues = validateTypeMatch(value, expectedType, key);
				issues.addAll(typeIssues);
			}

			// Enum 검증
			if (propSchema.has("enum")) {
				JsonNode enumNode = propSchema.get("enum");
				if (enumNode.isArray()) {
					boolean found = false;
					String actualValue = value.isTextual() ? value.asText() : value.toString();
					for (JsonNode enumVal : enumNode) {
						String enumStr = enumVal.isTextual() ? enumVal.asText() : enumVal.toString();
						if (enumStr.equals(actualValue)) {
							found = true;
							break;
						}
					}
					if (!found) {
						issues.add("enum 불일치 [" + key + "]: " + actualValue + " (허용값: " + enumNode + ")");
					}
				}
			}
		}

		return issues;
	}

	private List<String> validateTypeMatch(JsonNode value, String expectedType, String fieldName) {
		boolean valid = switch (expectedType) {
			case "string" -> value.isTextual();
			case "integer" -> value.isIntegralNumber();
			case "number" -> value.isNumber();
			case "boolean" -> value.isBoolean();
			case "array" -> value.isArray();
			case "object" -> value.isObject();
			default -> true;
		};

		if (!valid) {
			return List.of("type 불일치 [" + fieldName + "]: expected " + expectedType + ", actual " + value.getNodeType().toString().toLowerCase());
		}
		return Collections.emptyList();
	}

	// ── QueryParams 검증 ──

	private List<String> validateQueryParams(QaCase qa, List<SwaggerParameter> params) {
		Set<String> qaKeys = parseQueryParamKeys(qa.getQueryParams());
		boolean qaEmpty = qaKeys.isEmpty();

		if (params.isEmpty()) {
			return qaEmpty ? Collections.emptyList() : List.of("queryParams가 존재하지만 Swagger에 query 파라미터가 정의되어 있지 않음");
		}

		if (qaEmpty) {
			// 스키마에 required 파라미터가 있는지 확인
			List<String> issues = new ArrayList<>();
			for (SwaggerParameter param : params) {
				if (Boolean.TRUE.equals(param.getRequired())) {
					issues.add("required query 파라미터 누락: " + param.getName());
				}
			}
			return issues;
		}

		List<String> issues = new ArrayList<>();

		// 스키마 정의 파라미터 이름 수집
		Set<String> schemaParamNames = new HashSet<>();
		for (SwaggerParameter param : params) {
			schemaParamNames.add(param.getName());
		}

		// 1. 미정의 키 확인
		for (String key : qaKeys) {
			if (!schemaParamNames.contains(key)) {
				issues.add("미정의 query 파라미터: " + key);
			}
		}

		// 2. Required 검증
		for (SwaggerParameter param : params) {
			if (Boolean.TRUE.equals(param.getRequired()) && !qaKeys.contains(param.getName())) {
				issues.add("required query 파라미터 누락: " + param.getName());
			}
		}

		return issues;
	}

	private Set<String> parseQueryParamKeys(String queryParamsJson) {
		if (queryParamsJson == null || queryParamsJson.isBlank()) {
			return Collections.emptySet();
		}
		try {
			JsonNode node = objectMapper.readTree(queryParamsJson);
			if (!node.isObject()) return Collections.emptySet();
			Set<String> keys = new HashSet<>();
			node.fieldNames().forEachRemaining(keys::add);
			return keys;
		} catch (Exception e) {
			log.warn("queryParams JSON 파싱 실패: {}", e.getMessage());
			return Collections.emptySet();
		}
	}
}
