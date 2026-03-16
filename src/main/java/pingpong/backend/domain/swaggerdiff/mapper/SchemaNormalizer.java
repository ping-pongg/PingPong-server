package pingpong.backend.domain.swaggerdiff.mapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SchemaNormalizer {

	private final ObjectMapper objectMapper;

	public JsonNode normalize(Schema<?> schema, Map<String, Schema> allSchemas) {
		return normalize(schema, allSchemas, new HashSet<>());
	}

	public JsonNode normalize(MediaType mediaType, Map<String, Schema> schemas) {
		if (mediaType == null || mediaType.getSchema() == null) return null;
		return normalize(mediaType.getSchema(), schemas);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	JsonNode normalize(Schema<?> schema, Map<String, Schema> allSchemas, Set<String> visiting) {
		if (schema == null) return null;

		if (schema.get$ref() != null) {
			String refName = extractSchemaName(schema.get$ref());
			if (refName != null && allSchemas != null) {
				if (visiting.contains(refName)) {
					ObjectNode refNode = objectMapper.createObjectNode();
					refNode.put("$ref", schema.get$ref());
					return refNode;
				}
				Schema resolved = allSchemas.get(refName);
				if (resolved != null) {
					visiting.add(refName);
					JsonNode result = normalize(resolved, allSchemas, visiting);
					visiting.remove(refName);
					return result;
				}
			}
			ObjectNode refNode = objectMapper.createObjectNode();
			refNode.put("$ref", schema.get$ref());
			return refNode;
		}

		ObjectNode node = objectMapper.createObjectNode();

		// OAS 3.0: getType() → String, OAS 3.1: getTypes() → Set<String>
		String type = schema.getType();
		if (type == null && schema.getTypes() != null) {
			type = schema.getTypes().stream()
				.filter(t -> t != null && !"null".equals(t))
				.findFirst()
				.orElse(null);
		}
		if (schema.getFormat() != null) node.put("format", schema.getFormat());
		if (schema.getDescription() != null) node.put("description", schema.getDescription());
		if (schema.getExample() != null) {
			node.set("exampleValue", objectMapper.valueToTree(schema.getExample()));
		} else if (type != null) {
			node.put("exampleValue", type);
		}

		if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
			ArrayNode reqArr = objectMapper.createArrayNode();
			schema.getRequired().forEach(reqArr::add);
			node.set("required", reqArr);
		}

		if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
			ObjectNode propsNode = objectMapper.createObjectNode();
			for (Map.Entry<String, Schema> entry :
					((Map<String, Schema>) (Map<?, ?>) schema.getProperties()).entrySet()) {
				JsonNode propNode = normalize(entry.getValue(), allSchemas, visiting);
				if (propNode != null) propsNode.set(entry.getKey(), propNode);
			}
			if (!propsNode.isEmpty()) node.set("properties", propsNode);
		}

		if (schema.getItems() != null) {
			JsonNode itemsNode = normalize(schema.getItems(), allSchemas, visiting);
			if (itemsNode != null) node.set("items", itemsNode);
		}

		if (schema.getOneOf() != null && !schema.getOneOf().isEmpty())
			node.set("oneOf", buildComposedArray(schema.getOneOf(), allSchemas, visiting));
		if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty())
			node.set("anyOf", buildComposedArray(schema.getAnyOf(), allSchemas, visiting));
		if (schema.getAllOf() != null && !schema.getAllOf().isEmpty())
			node.set("allOf", buildComposedArray(schema.getAllOf(), allSchemas, visiting));

		if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
			ArrayNode arr = objectMapper.createArrayNode();
			for (Object e : schema.getEnum()) {
				if (e != null) arr.add(e.toString());
			}
			node.set("enum", arr);
		}

		return node.isEmpty() ? null : node;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private ArrayNode buildComposedArray(List<?> schemaList, Map<String, Schema> allSchemas, Set<String> visiting) {
		ArrayNode arr = objectMapper.createArrayNode();
		((List<Schema>) (List<?>) schemaList).stream()
			.map(s -> normalize(s, allSchemas, visiting))
			.filter(Objects::nonNull)
			.forEach(arr::add);
		return arr;
	}

	public String extractType(Schema<?> schema) {
		if (schema == null) return null;
		if (schema.get$ref() != null) return "ref";
		String type = schema.getType();
		if (type == null && schema.getTypes() != null) {
			type = schema.getTypes().stream()
				.filter(t -> t != null && !"null".equals(t))
				.findFirst()
				.orElse(null);
		}
		if (type == null) return null;
		if ("array".equals(type)) {
			String inner = extractType(schema.getItems());
			return inner != null ? "array<" + inner + ">" : "array";
		}
		return type;
	}

	private String extractSchemaName(String ref) {
		if (ref == null) return null;
		int idx = ref.lastIndexOf('/');
		return idx >= 0 ? ref.substring(idx + 1) : ref;
	}
}
