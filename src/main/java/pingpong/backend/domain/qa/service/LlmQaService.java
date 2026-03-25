package pingpong.backend.domain.qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import pingpong.backend.domain.qa.dto.QaScenarioResponse; // 이전에 정의한 DTO
import pingpong.backend.domain.swagger.dto.EndpointAggregate;
import pingpong.backend.global.exception.CustomException;

import java.util.Map;

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
   
   [핵심 제약 조건 - 필독]
   1. 모든 응답 필드는 절대 null이거나 비어있을 수 없습니다. 
   2. 명세에 구체적인 샘플 데이터가 없더라도, 필드명과 타입을 바탕으로 가장 적절한 '가상의 테스트 데이터'를 직접 생성해서 채우세요.
   3. 반드시 유효한 단일 JSON 객체로만 응답하며, ```json ... ``` 코드 블록을 사용하세요.
   4. 서론, 결론, 부연 설명은 절대 금지합니다.
   
   [출력 JSON 구조 명세]
   {
     "endpointId": number,
     "scenarios": [
       {
         "scenarioName": "string (구체적 명칭)",
         "testType": "POSITIVE | NEGATIVE | SECURITY",
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
           "bodyFields": "object"
         }
       }
     ]
   }
   
   [테스트 설계 가이드라인]
   - 최소 3개 이상의 시나리오를 생성하세요 (정상 케이스 1개, 예외 케이스 2개 이상).
   - [중요] API 경로에 {variable} 형태가 있다면 반드시 pathVariables에 해당 값을 정의하세요.
   - [중요] 페이지네이션, 검색어 등 선택적 파라미터가 명세에 있다면 queryParams에 포함시키세요.
   - [중요] pathVariables와 queryParams의 값은 반드시 해당 파라미터의 schema 타입에 맞아야 합니다.
     예: schema가 {"type":"integer"}이면 "123"처럼 숫자 문자열만 사용하고, "team123" 같은 값은 절대 금지합니다.
   - [중요] headers에 Authorization 헤더는 절대 포함하지 마세요. 인증은 별도 시스템에서 처리됩니다.
   - Request Body의 필드가 명세에 있다면, 데이터 타입에 맞는 유효한 값을 반드시 생성하세요.
   - 예외 케이스(NEGATIVE)의 경우 400, 401, 404 등 적절한 응답 코드를 할당하세요.
   - [중요] integer 타입의 ID 파라미터(예: teamId, userId 등)의 경우, POSITIVE 케이스에서는 반드시 1을 사용하세요.
   - [중요] NEGATIVE 케이스 중 '리소스를 찾을 수 없음(404)'을 테스트하는 경우에만 존재하지 않는 값(예: 999999)을 사용하세요.
     그 외 NEGATIVE 케이스(잘못된 body, 인증 실패 등)에서는 POSITIVE와 동일한 유효한 값을 사용하세요.
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
						Map<String, Object> paramMap = new java.util.HashMap<>();
						paramMap.put("name", p.getName());
						paramMap.put("in", p.getInType());
						paramMap.put("required", p.getRequired());
						paramMap.put("schema", p.getSchemaJson() != null ? p.getSchemaJson() : "{}");
						return paramMap;
					})
					.toList(),
				"requests", spec.requests().stream() // 필드명 수정됨
					.map(req -> Map.of(
						"contentType", req.getMediaType(),
						"schema", req.getSchemaJson() != null ? req.getSchemaJson() : "No Schema Provided"
					)).toList(),
				"responses", spec.responses().stream()
					.map(res -> Map.of("statusCode", res.getStatusCode(), "description", res.getDescription()))
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

            [필독: Request Body 생성 규칙]
            - 메서드가 POST, PUT, PATCH인 경우, 'requestData.body' 필드는 절대 null일 수 없습니다.
            - 제공된 'requests'의 'schema' 정보를 분석하여 실제 요청에 사용 가능한 JSON 데이터를 생성하세요.
            - 만약 'schema' 내용이 부족하거나 "No Schema Provided"인 경우, API 경로(%s)와 summary를 통해
              일반적으로 필요한 필드(예: title, content, name 등)를 유추하여 반드시 'body'를 채워야 합니다.
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
}