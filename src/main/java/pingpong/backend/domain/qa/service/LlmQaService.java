package pingpong.backend.domain.qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import pingpong.backend.domain.qa.dto.QaScenarioResponse; // 이전에 정의한 DTO
import pingpong.backend.domain.swagger.dto.EndpointAggregate;
import pingpong.backend.global.exception.CustomException;

import java.util.*;

@Slf4j
@Service
public class LlmQaService {

	private final ChatClient qaChatClient;
	private final ObjectMapper objectMapper;

	// 생성자 주입 (@Qualifier 확인 필수)
	public LlmQaService(@Qualifier("qaClient") ChatClient qaChatClient,
		ObjectMapper objectMapper) {
		this.qaChatClient = qaChatClient;
		this.objectMapper = objectMapper;
	}

	// ── System Prompt (역할 + 지표 정의 + 출력 규칙) ─────────────────────────────
	private static final String QA_SYSTEM_PROMPT = """
   당신은 전문 QA 엔지니어이자 테스트 자동화 전문가입니다.
   제공되는 API 명세를 분석하여 실무 수준의 테스트 시나리오를 생성하세요.
   
   [절대 규칙 - 위반 시 잘못된 응답으로 간주]
	1. 반드시 유효한 단일 JSON 객체만 응답하세요.
	2. 응답은 ```json ... ``` 코드 블록으로만 감싸세요.
	3. 서론, 결론, 설명 문장, 주석은 절대 포함하지 마세요.
	4. 모든 문자열 필드는 null, 빈 문자열, 공백 문자열일 수 없습니다.
	5. 모든 객체 필드는 반드시 존재해야 하며, 없으면 빈 객체 {}를 사용하세요.
	6. POST, PUT, PATCH 요청의 requestData.body 는 절대 null일 수 없습니다.
	7. POSITIVE 시나리오는 반드시 실제 서버에서 성공 가능한 요청이어야 합니다.
	8. POSITIVE 시나리오에서 validation 오류가 발생할 수 있는 값은 절대 사용하지 마세요.
	9. 테스트 데이터는 반드시 "현실적인 값"이 아니라 "검증 통과 가능한 값"을 기준으로 생성하세요.
	10. schema 에 명시된 type, required, enum, format, min/max, minLength/maxLength 조건이 있으면 반드시 지키세요.
   
   
   [출력 JSON 구조 명세]
   {
     "endpointId": number,
     "scenarios": [
       {
         "scenarioName": "string (구체적 명칭)",
         "testType": "POSITIVE | NEGATIVE",
         "description": "string (테스트 목적과 기대 결과)",
         "requestData": {
           "method": "string (GET, POST 등)",
           "url": "string (변수가 치환되지 않은 원본 경로, 예: /api/v1/users/{userId})",
           "pathVariables": { "key": "value" }, // URL 경로에 포함된 변수들 (없으면 {})
           "queryParams": { "key": "value" },   // URL 뒤에 ?key=value로 붙을 파라미터들 (없으면 {})
           "headers": { "Content-Type": "application/json" },
           "body": "object | null"
         },
         "expectedResponse": {
           "statusCode": number,
         "bodyFields": {
          "key": "expected value or expected type"
         } 
       }
     ]
   }
   
	[시나리오 개수 규칙]
	- 반드시 최소 3개의 시나리오를 생성하세요.
	- 반드시 다음 조합을 포함하세요:
	  1) POSITIVE 1개
	  2) NEGATIVE 2개   

	[POSITIVE 시나리오 생성 규칙]
	- 반드시 정상적으로 성공하는 요청 1개를 생성하세요.
	- required 필드는 모두 포함하세요.
	- path variable 이 있으면 반드시 pathVariables 에 채우세요.
	- query parameter 가 있으면 schema 타입에 맞게 채우세요.
	- integer 타입의 ID 파라미터는 POSITIVE 에서 반드시 1을 사용하세요.
	- body 필드는 schema 에 맞는 유효한 JSON 객체를 생성하세요.
	- string 값은 의미 있는 값으로 작성하세요.
	  예: "testUser", "sample title", "user@example.com", "2024-01-01"
	- enum 이 있으면 반드시 enum 내부 값 중 하나를 사용하세요.
	- format 이 있으면 반드시 맞는 형식을 사용하세요.
	  예: email, date, date-time, uuid

	[NEGATIVE 시나리오 생성 규칙]
	NEGATIVE 시나리오는 반드시 아래 2가지 유형만 생성하세요.
	1. 필수값 누락 또는 잘못된 형식/타입으로 인해 400이 발생하는 경우
	2. 존재하지 않는 리소스 ID로 인해 404가 발생하는 경우
	
	[NEGATIVE 상세 규칙]
	- 404 시나리오에서만 존재하지 않는 ID 값 999999 를 사용하세요.
	- 400 시나리오에서는 pathVariables 는 POSITIVE 와 동일한 유효한 값을 사용하세요.
	- 400 시나리오에서는 body 또는 query parameter 의 필수값 누락, 타입 오류, format 오류 중 하나만 명확하게 사용하세요.
	- 여러 오류를 한 번에 섞지 마세요.
	- 반드시 실패 원인이 명확해야 합니다.
	- 실패가 애매한 시나리오는 생성하지 마세요.

	[파라미터 규칙]
	- API 경로에 {variable} 형태가 있으면 반드시 pathVariables 에 값을 정의하세요.
	- pathVariables 와 queryParams 의 값은 반드시 해당 schema 타입에 맞아야 합니다.
	- integer 타입이면 숫자 문자열만 사용하세요.
	- boolean 타입이면 true/false 를 사용하세요.
	- 배열이면 배열 형식으로 표현하세요.
	- Authorization 헤더는 절대 포함하지 마세요.
	
	[응답 예측 규칙]
	- expectedResponse.statusCode 는 반드시 실제로 기대되는 대표 상태코드만 작성하세요.
	- expectedResponse.bodyFields 는 전체 응답을 추측하지 말고, 검증 가능한 핵심 필드만 작성하세요.
	- 명세에 없는 응답 필드를 임의로 만들지 마세요.
	
	[중요]
	- 이 작업의 목적은 "문서 설명"이 아니라 "실행 가능한 테스트 케이스 생성"입니다.
	- POSITIVE 시나리오 실패는 심각한 오류입니다.
	- NEGATIVE 시나리오는 반드시 실패가 보장되는 경우만 생성하세요.
   """;

	// ── Repair Prompt ──────────────────────────────────────────────────────────
	private static final String REPAIR_TEMPLATE = """
            아래 JSON이 손상되었습니다. 다른 설명 없이 오직 교정된 JSON 코드 블록만 반환하세요.
            
            손상된 내용:
            {raw}
            
            교정된 JSON:
            """;

	/**
	 * 최종 시나리오 생성 실행 메서드
	 */
	public QaOutcome generateScenarios(EndpointAggregate spec, Map<String, String> paramDefaults) {
		String userPrompt = buildUserPrompt(spec, paramDefaults);

		String raw;
		try {
			// Bean에 설정된 QA_SYSTEM_PROMPT를 기본으로 사용
			raw = qaChatClient.prompt()
				.system(QA_SYSTEM_PROMPT)
				.user(userPrompt)
				.call()
				.content();
		} catch (Exception e) {
			log.error("QA-GEN: LLM 호출 실패", e);
			return new QaOutcome(null, null);
		}

		// Step 1: 기본 파싱 시도
		QaScenarioResponse result = tryParse(raw);
		if (result != null) {
			log.info("QA-GEN: Step1 파싱 성공 - endpointId={}", spec.endpoint().getId());
			return new QaOutcome(result, raw);
		}

		// Step 2: 파싱 실패 시 Repair 프롬프트 실행
		log.warn("QA-GEN: Step1 파싱 실패, repair 시도 - endpointId={}", spec.endpoint().getId());
		String repairedRaw = repairJson(raw);
		result = tryParse(repairedRaw);
		if (result != null) {
			log.info("QA-GEN: Step2 Repair 파싱 성공");
			return new QaOutcome(result, repairedRaw);
		}

		// Step 3: 전체 재생성 1회 시도
		log.warn("QA-GEN: repair 실패, 전체 재생성 시도 - endpointId={}", spec.endpoint().getId());
		try {
			String retryRaw = qaChatClient.prompt()
				.system(QA_SYSTEM_PROMPT)
				.user(userPrompt)
				.call()
				.content();
			result = tryParse(retryRaw);
			if (result != null) {
				log.info("QA-GEN: Step3 재생성 파싱 성공 - endpointId={}", spec.endpoint().getId());
				return new QaOutcome(result, retryRaw);
			}
		} catch (Exception e) {
			log.error("QA-GEN: 재생성 호출 실패", e);
		}

		log.error("QA-GEN: 모든 시도 후에도 파싱 실패 - raw='{}'", raw);
		return new QaOutcome(null, raw);
	}

	private QaScenarioResponse tryParse(String raw) {
		if (raw == null || raw.isBlank()) return null;
		String json = extractJson(raw);
		try {
			return objectMapper.readValue(json, QaScenarioResponse.class);
		} catch (Exception e) {
			log.debug("QA-GEN: tryParse 실패 - {}", e.getMessage());
			return null;
		}
	}

	private String repairJson(String raw) {
		if (raw == null) return "{}";
		try {
			return qaChatClient.prompt()
				.user(REPAIR_TEMPLATE.replace("{raw}", raw))
				.call()
				.content();
		} catch (Exception e) {
			log.error("QA-GEN: Repair 호출 자체 실패", e);
			return "{}";
		}
	}

	private String extractJson(String raw) {
		if (raw == null) return "{}";
		int start = raw.indexOf('{');
		int end = raw.lastIndexOf('}');
		return (start >= 0 && end > start) ? raw.substring(start, end + 1) : raw.trim();
	}

	/**
	 * 이전에 고민했던 JsonProcessingException 방지를 위해
	 * 엔티티 대신 필요한 필드만 Map으로 추출하여 전달합니다.
	 */
	public String buildUserPrompt(EndpointAggregate spec, Map<String, String> paramDefaults) {
		try {
			// 1. AI 분석에 꼭 필요한 정보만 단순 Map 구조로 변환 (순환 참조 방지 및 토큰 최적화)
			Map<String, Object> simplifiedSpec = Map.of(
				"endpoint", Map.of(
					"id", spec.endpoint().getId(),
					"path", spec.endpoint().getPath(),
					"method", spec.endpoint().getMethod(),
					"summary", spec.endpoint().getSummary() != null ? spec.endpoint().getSummary() : ""
				),
				"security", spec.endpointSecuritys().stream() // 필드명 수정됨
					.map(s -> Map.of("type", s.getType(), "name", s.getHeaderName()))
					.toList(),
				"parameters", spec.parameters().stream()
					.map(p -> {
						Map<String, Object> constraint = extractParameterConstraint(
								p.getSchemaJson() != null ? p.getSchemaJson() : "{}"
						);
						Map<String, Object> paramMap = new LinkedHashMap<>();
						paramMap.put("name", p.getName());
						paramMap.put("in", p.getInType());
						paramMap.put("required", p.getRequired());
						paramMap.put("type", constraint.get("type"));
						paramMap.put("rules", constraint.get("rules"));
//						paramMap.put("schema", p.getSchemaJson() != null ? p.getSchemaJson() : "{}");
						return paramMap;
					})
					.toList(),
					"requests", spec.requests().stream()
							.map(req -> {
								List<Map<String, Object>> bodyConstraints = extractBodyConstraints(
										req.getSchemaJson() != null ? req.getSchemaJson() : "No Schema Provided"
								);

								Map<String, Object> reqMap = new LinkedHashMap<>();
								reqMap.put("contentType", req.getMediaType());
								reqMap.put("bodyConstraints", bodyConstraints);
								reqMap.put("rawSchema", req.getSchemaJson() != null ? req.getSchemaJson() : "No Schema Provided");
								return reqMap;
							})
							.toList(),
					"responses", spec.responses().stream()
							.map(res -> Map.of(
									"statusCode", res.getStatusCode(),
									"description", res.getDescription() != null ? res.getDescription() : ""
							))
							.toList()
			);

			String swaggerJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(simplifiedSpec);

			// 2. 사용자가 설정한 path variable 기본값 섹션 생성
			String paramDefaultsSection = "";
			if (paramDefaults != null && !paramDefaults.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				sb.append("""

            [사전 정의된 Path Variable 값]
            아래 값들은 실제 서버에 존재하는 유효한 값입니다.
            POSITIVE 케이스와 리소스 존재를 전제로 하는 NEGATIVE 케이스에서 반드시 이 값을 사용하세요.
            """);
				paramDefaults.forEach((name, value) ->
					sb.append("            - ").append(name).append(": ").append(value).append("\n"));
				paramDefaultsSection = sb.toString();
			}

			// 3. 고정된 유저 프롬프트 (Body 생성을 강제하는 지시사항 포함)
			return String.format("""
            분석 대상 API (ID: %d)의 명세를 바탕으로 실행 가능한 QA 시나리오를 생성하세요.

            [검증 규칙 해석 방법]
            - parameters 의 type, rules 는 서버 명세에서 추출한 검증 규칙입니다.
            - requests 의 bodyConstraints 는 Request Body 필드별 검증 규칙입니다.
            - POSITIVE 시나리오는 이 규칙을 모두 만족해야 합니다.
            - NEGATIVE 시나리오는 반드시 하나의 규칙만 명확하게 위반해야 합니다.
            - enum 이 있으면 반드시 제공된 값 중 하나만 사용하세요.
            - format 이 있으면 반드시 그 형식에 맞는 값을 사용하세요.
            - required=true 인 필드는 POSITIVE 에서 반드시 포함해야 합니다.

            [필독: Request Body 생성 규칙]
            - 메서드가 POST, PUT, PATCH인 경우, 'requestData.body' 필드는 절대 null일 수 없습니다.
            - bodyConstraints 가 존재하면 반드시 그 규칙을 기준으로 body 를 생성하세요.
            - bodyConstraints 가 비어 있고 rawSchema 가 "No Schema Provided" 인 경우에만,
              API 경로(%s)와 summary를 기반으로 일반적으로 필요한 필드를 유추하여 body 를 생성하세요.

            %s

            [분석할 API 명세]
            %s
            """, spec.endpoint().getId(), spec.endpoint().getPath(), paramDefaultsSection, swaggerJson);
		} catch (JsonProcessingException e) {
			log.error("QA-GEN: UserPrompt 생성 중 직렬화 오류 발생", e);
			return "데이터 변환 중 오류가 발생했습니다.";
		}
	}

	public record QaOutcome(QaScenarioResponse result, String raw) {}

	private Map<String, Object> extractParameterConstraint(String schemaJson) {
		if (schemaJson == null || schemaJson.isBlank() || "{}".equals(schemaJson)) {
			return Map.of("type", "unknown", "rules", List.of());
		}

		try {
			JsonNode root = objectMapper.readTree(schemaJson);

			String type = text(root, "type");
			String format = text(root, "format");

			List<String> rules = new ArrayList<>();

			if (format != null) rules.add("format=" + format);
			if (root.has("enum")) rules.add("enum=" + root.get("enum").toString());
			if (root.has("minimum")) rules.add("minimum=" + root.get("minimum").asText());
			if (root.has("maximum")) rules.add("maximum=" + root.get("maximum").asText());
			if (root.has("minLength")) rules.add("minLength=" + root.get("minLength").asText());
			if (root.has("maxLength")) rules.add("maxLength=" + root.get("maxLength").asText());
			if (root.has("pattern")) rules.add("pattern=" + root.get("pattern").asText());

			return Map.of(
					"type", type != null ? type : "unknown",
					"rules", rules
			);
		} catch (Exception e) {
			log.debug("QA-GEN: parameter constraint 추출 실패 - {}", e.getMessage());
			return Map.of("type", "unknown", "rules", List.of("schema_parse_failed"));
		}
	}

	private List<Map<String, Object>> extractBodyConstraints(String schemaJson) {
		if (schemaJson == null || schemaJson.isBlank() || "No Schema Provided".equals(schemaJson)) {
			return List.of();
		}

		try {
			JsonNode root = objectMapper.readTree(schemaJson);

			Set<String> requiredFields = new HashSet<>();
			JsonNode requiredNode = root.get("required");
			if (requiredNode != null && requiredNode.isArray()) {
				for (JsonNode node : requiredNode) {
					requiredFields.add(node.asText());
				}
			}

			JsonNode propertiesNode = root.get("properties");
			if (propertiesNode == null || !propertiesNode.isObject()) {
				return List.of();
			}

			List<Map<String, Object>> result = new ArrayList<>();

			Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> entry = fields.next();

				String fieldName = entry.getKey();
				JsonNode fieldSchema = entry.getValue();

				List<String> rules = new ArrayList<>();

				String type = text(fieldSchema, "type");
				String format = text(fieldSchema, "format");

				if (format != null) rules.add("format=" + format);
				if (fieldSchema.has("enum")) rules.add("enum=" + fieldSchema.get("enum").toString());
				if (fieldSchema.has("minimum")) rules.add("minimum=" + fieldSchema.get("minimum").asText());
				if (fieldSchema.has("maximum")) rules.add("maximum=" + fieldSchema.get("maximum").asText());
				if (fieldSchema.has("minLength")) rules.add("minLength=" + fieldSchema.get("minLength").asText());
				if (fieldSchema.has("maxLength")) rules.add("maxLength=" + fieldSchema.get("maxLength").asText());
				if (fieldSchema.has("pattern")) rules.add("pattern=" + fieldSchema.get("pattern").asText());

				result.add(Map.of(
						"name", fieldName,
						"required", requiredFields.contains(fieldName),
						"type", type != null ? type : "unknown",
						"rules", rules
				));
			}

			return result;
		} catch (Exception e) {
			log.debug("QA-GEN: body constraint 추출 실패 - {}", e.getMessage());
			return List.of();
		}
	}

	private String text(JsonNode node, String fieldName) {
		JsonNode child = node.get(fieldName);
		return (child != null && !child.isNull()) ? child.asText() : null;
	}
}