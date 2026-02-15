package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import pingpong.backend.domain.notion.NotionErrorCode;
import pingpong.backend.domain.notion.client.NotionRestClient;
import pingpong.backend.domain.notion.dto.NotionCreateDatabaseRequest;
import pingpong.backend.domain.notion.util.NotionJsonUtils;
import pingpong.backend.domain.notion.util.NotionLogSupport;
import pingpong.backend.global.exception.CustomException;

import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class NotionDatabaseCreateService {

    private static final Logger log = LoggerFactory.getLogger(NotionDatabaseCreateService.class);
    private static final int MAX_LOG_BODY_CHARS = 10 * 1024;
    private static final Set<String> ALLOWED_PROPERTY_TYPES = Set.of("title", "rich_text", "number",
            "select", "multi_select", "date", "people", "checkbox", "url", "email", "phone_number", "files",
            "relation", "rollup", "formula", "created_time", "created_by", "last_edited_time", "last_edited_by"
    );

    private final NotionTokenService notionTokenService;
    private final NotionRestClient notionRestClient;
    private final NotionJsonUtils notionJsonUtils;
    private final ObjectMapper objectMapper;

    public JsonNode createDatabase(Long teamId, String parentPageId, NotionCreateDatabaseRequest payload) {
        if (payload == null) {
            throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
        }
        log.info("DB-CREATE: incoming request={}",
                NotionLogSupport.truncate(notionJsonUtils.writeJson(payload), MAX_LOG_BODY_CHARS));

        validateCreateDatabaseRequest(payload);

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode parent = objectMapper.createObjectNode();
        parent.put("page_id", parentPageId);
        body.set("parent", parent);

        body.set("title", notionJsonUtils.toRichTextArray(payload.title()));
        if (payload.description() != null && !payload.description().isBlank()) {
            body.set("description", notionJsonUtils.toRichTextArray(payload.description()));
        }
        if (payload.isInline() != null) {
            body.put("is_inline", payload.isInline());
        }
        if (payload.icon() != null) {
            body.set("icon", payload.icon());
        }
        if (payload.cover() != null) {
            body.set("cover", payload.cover());
        }

        ObjectNode propertiesNode = objectMapper.createObjectNode();
        payload.properties().forEach(propertiesNode::set);
        body.set("properties", propertiesNode);

        log.info("DB-CREATE: payload={}",
                NotionLogSupport.truncate(notionJsonUtils.writeJson(body), MAX_LOG_BODY_CHARS));

        ResponseEntity<String> response = callApi(teamId,
                () -> notionRestClient.post("/v1/databases", notionTokenService.getAccessToken(teamId), body));

        JsonNode result = notionJsonUtils.parseJson(response);
        log.info("DB-CREATE: responseStatus={}", response.getStatusCode().value());
        return result;
    }

    private ResponseEntity<String> callApi(Long teamId, Supplier<ResponseEntity<String>> supplier) {
        ResponseEntity<String> response = notionTokenService.executeWithRefresh(teamId, supplier);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }
        return response;
    }

    private void validateCreateDatabaseRequest(NotionCreateDatabaseRequest payload) {
        if (payload.title() == null || payload.title().isBlank()) {
            throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
        }
        if (payload.properties() == null || payload.properties().isEmpty()) {
            throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
        }
        int titleCount = 0;
        for (var entry : payload.properties().entrySet()) {
            String name = entry.getKey();
            JsonNode node = entry.getValue();
            if (name == null || name.isBlank() || node == null || !node.isObject()) {
                throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
            }
            JsonNode typeNode = node.get("type");
            if (typeNode == null || typeNode.asText().isBlank()) {
                throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
            }
            String type = typeNode.asText();
            if (!isAllowedPropertyType(type)) {
                throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
            }
            int fieldCount = node.size();
            if (fieldCount > 2) {
                throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
            }
            if (!node.has(type)) {
                throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
            }
            if ("title".equals(type)) {
                titleCount++;
            }
        }
        if (titleCount != 1) {
            throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
        }
    }

    private boolean isAllowedPropertyType(String type) {
        return ALLOWED_PROPERTY_TYPES.contains(type);
    }
}
