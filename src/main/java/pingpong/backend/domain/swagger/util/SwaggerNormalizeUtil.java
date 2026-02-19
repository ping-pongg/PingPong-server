package pingpong.backend.domain.swagger.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class SwaggerNormalizeUtil {

	private static final ObjectMapper mapper = new ObjectMapper()
		.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
		.configure(SerializationFeature.INDENT_OUTPUT, false);

	public static String normalizeJson(String rawJson) {
		try {
			JsonNode root = mapper.readTree(rawJson);

			// 정렬된 canonical JSON 반환
			return mapper.writeValueAsString(root);

		} catch (Exception e) {
			throw new RuntimeException("JSON normalize 실패", e);
		}
	}

	/**
	 * 해시값 생성을 위해 swagger JSON을 비교용으로 정규화
	 * @param node
	 * @return
	 */
	public static JsonNode normalizeNode(JsonNode node) {

		if (node.isObject()) {
			ObjectNode sorted = mapper.createObjectNode();

			List<String> fieldNames=new ArrayList<>();
			node.fieldNames().forEachRemaining(fieldNames::add);
			Collections.sort(fieldNames);

			//모든 필드 순회
			for(String field:fieldNames){
				//무시할 필드 제거
				if (isIgnorableField(field)) {
					continue;
				}
				//json 트리 전체 재귀 순회
				JsonNode child = node.get(field);
				if (child != null && !child.isNull()) {
					sorted.set(field, normalizeNode(child));
				}
			}

			return sorted;
		}

		if (node.isArray()) {
			ArrayNode array = mapper.createArrayNode();
			//배열 내부 순회하며 정규화
			for (JsonNode element : node) {
				array.add(normalizeNode(element));
			}
			return array;
		}
		return node;
	}

	private static boolean isIgnorableField(String field) {
		return field.equals("description")
			|| field.equals("example")
			|| field.equals("externalDocs")
			|| field.equals("summary");
	}

}
