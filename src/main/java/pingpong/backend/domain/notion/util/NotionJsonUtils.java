package pingpong.backend.domain.notion.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotionJsonUtils {

    private final ObjectMapper objectMapper;

    public JsonNode parseJson(ResponseEntity<String> response) {
        if (response == null || response.getBody() == null || response.getBody().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("raw", response.getBody());
            return fallback;
        }
    }

    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    public ArrayNode toRichTextArray(String text) {
        ArrayNode array = objectMapper.createArrayNode();
        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", "text");
        ObjectNode textNode = objectMapper.createObjectNode();
        textNode.put("content", text);
        item.set("text", textNode);
        array.add(item);
        return array;
    }

    public String extractDataSourceId(JsonNode databaseNode) {
        String direct = databaseNode.path("data_source_id").asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        return databaseNode.path("data_source").path("id").asText(null);
    }
}
