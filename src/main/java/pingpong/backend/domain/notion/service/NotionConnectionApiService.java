package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.notion.Notion;
import pingpong.backend.domain.notion.NotionErrorCode;
import pingpong.backend.domain.notion.client.NotionRestClient;
import pingpong.backend.domain.notion.repository.NotionRepository;
import pingpong.backend.domain.notion.util.NotionJsonUtils;
import pingpong.backend.global.exception.CustomException;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotionConnectionApiService {

    private final NotionTokenService notionTokenService;
    private final NotionRestClient notionRestClient;
    private final NotionRepository notionRepository;
    private final NotionJsonUtils notionJsonUtils;
    private final ObjectMapper objectMapper;

    public JsonNode listCandidateDatabases(Long teamId) {
        Map<String, Object> filter = new HashMap<>();
        filter.put("property", "object");
        filter.put("value", "database");

        Map<String, Object> sort = new HashMap<>();
        sort.put("timestamp", "last_edited_time");
        sort.put("direction", "descending");

        Map<String, Object> body = new HashMap<>();
        body.put("filter", filter);
        body.put("page_size", 100);
        body.put("sort", sort);

        ResponseEntity<String> response = callApi(teamId,
                () -> notionRestClient.post("/v1/search", notionTokenService.getAccessToken(teamId), body));
        return filterDatabaseSearchResponse(response.getBody());
    }

    @Transactional
    public void connectDatabase(Long teamId, String databaseId) {
        ResponseEntity<String> databaseResponse = callApi(teamId,
                () -> notionRestClient.get("/v1/databases/" + databaseId, notionTokenService.getAccessToken(teamId)));

        JsonNode databaseNode = notionJsonUtils.parseJson(databaseResponse);
        String dataSourceId = notionJsonUtils.extractDataSourceId(databaseNode);

        Notion notion = notionTokenService.getNotionOrThrow(teamId);
        notion.updateConnection(databaseId, dataSourceId);
        notionRepository.save(notion);
    }

    private ResponseEntity<String> callApi(Long teamId, java.util.function.Supplier<ResponseEntity<String>> supplier) {
        ResponseEntity<String> response = notionTokenService.executeWithRefresh(teamId, supplier);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }
        return response;
    }

    private JsonNode filterDatabaseSearchResponse(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode results = root.path("results");
            ArrayNode filtered = objectMapper.createArrayNode();
            if (results.isArray()) {
                for (JsonNode item : results) {
                    ObjectNode out = objectMapper.createObjectNode();
                    ArrayNode titles = objectMapper.createArrayNode();
                    JsonNode titleNodes = item.path("title");
                    if (titleNodes.isArray()) {
                        for (JsonNode titleNode : titleNodes) {
                            String plain = titleNode.path("plain_text").asText(null);
                            if (plain != null) {
                                titles.add(plain);
                            }
                        }
                    }
                    out.set("title", titles);
                    out.put("id", item.path("id").asText(null));
                    out.put("last_edited_time", item.path("last_edited_time").asText(null));
                    out.put("url", item.path("url").asText(null));
                    filtered.add(out);
                }
            }
            return filtered;
        } catch (Exception e) {
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }
    }
}
