package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import pingpong.backend.domain.notion.NotionErrorCode;
import pingpong.backend.domain.notion.client.NotionRestClient;
import pingpong.backend.domain.notion.dto.NotionCreatePageRequest;
import pingpong.backend.domain.notion.dto.NotionDatabaseQueryRequest;
import pingpong.backend.domain.notion.dto.NotionDateRange;
import pingpong.backend.domain.notion.dto.NotionPageUpdateRequest;
import pingpong.backend.domain.notion.service.NotionPropertyResolver.PropertyNames;
import pingpong.backend.domain.notion.util.NotionJsonUtils;
import pingpong.backend.domain.notion.util.NotionLogSupport;
import pingpong.backend.global.exception.CustomException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static pingpong.backend.domain.notion.util.NotionDateValidator.isIsoDateOrDateTime;

@Service
@RequiredArgsConstructor
public class NotionPageService {

    private static final Logger log = LoggerFactory.getLogger(NotionPageService.class);
    private static final int MAX_LOG_BODY_CHARS = 10 * 1024;
    private static final int MAX_CHILD_DEPTH = 4;
    private static final Duration DATABASE_SCHEMA_TTL = Duration.ofMinutes(5);

    private final NotionConnectionService notionConnectionService;
    private final NotionDatabaseQueryService notionDatabaseQueryService;
    private final NotionTokenService notionTokenService;
    private final NotionRestClient notionRestClient;
    private final NotionPropertyResolver propertyResolver;
    private final NotionJsonUtils notionJsonUtils;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CachedDatabase> databaseCache = new ConcurrentHashMap<>();

    private record CachedDatabase(JsonNode node, Instant cachedAt) {
    }

    public JsonNode getPageBlocks(Long teamId,
                                  String pageId,
                                  Integer pageSize,
                                  String startCursor,
                                  boolean deep,
                                  NotionDatabaseQueryRequest databaseQueryRequest) {
        Map<String, Object> queryParams = new HashMap<>();
        if (pageSize != null) {
            queryParams.put("page_size", pageSize);
        }
        if (startCursor != null && !startCursor.isBlank()) {
            queryParams.put("start_cursor", startCursor);
        }

        ResponseEntity<String> response = callApi(teamId,
                () -> notionRestClient.get("/v1/blocks/" + pageId + "/children",
                        notionTokenService.getAccessToken(teamId), queryParams));
        JsonNode root = notionJsonUtils.parseJson(response);
        if (deep) {
            attachChildrenRecursively(teamId, root, pageSize, 0);
        }
        ObjectNode aggregated = toObjectNode(root);
        ArrayNode childDatabases = fetchChildDatabases(teamId, root, databaseQueryRequest);
        aggregated.set("child_databases", childDatabases);
        return aggregated;
    }

    public JsonNode updatePage(Long teamId, String pageId, NotionPageUpdateRequest payload) {
        String databaseId = notionConnectionService.resolveConnectedDatabaseId(teamId);
        log.info("PAGE-UPDATE: incoming request={}", notionJsonUtils.writeJson(payload));

        if (payload == null || isEmptyUpdate(payload)) {
            throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
        }

        JsonNode databaseNode = getDatabaseNode(teamId, databaseId);
        PropertyNames propertyNames = propertyResolver.resolvePropertyNames(databaseNode);
        List<String> availableProperties = propertyResolver.resolvePropertyKeys(databaseNode);
        ObjectNode propertiesNode = objectMapper.createObjectNode();

        if (payload.title() != null && !payload.title().isBlank()) {
            propertyResolver.requireProperty(propertyNames.title(), "title", availableProperties);
            ObjectNode titleNode = objectMapper.createObjectNode();
            titleNode.set("title", notionJsonUtils.toRichTextArray(payload.title()));
            propertiesNode.set(propertyNames.title(), titleNode);
        }

        buildDateProperty(payload.date(), propertyNames, availableProperties, propertiesNode);

        if (payload.status() != null && !payload.status().isBlank()) {
            propertyResolver.requireProperty(propertyNames.status(), "status", availableProperties);
            ObjectNode statusValue = objectMapper.createObjectNode();
            statusValue.put("name", payload.status());
            ObjectNode statusNode = objectMapper.createObjectNode();
            statusNode.set("status", statusValue);
            propertiesNode.set(propertyNames.status(), statusNode);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.set("properties", propertiesNode);
        log.info("PAGE-UPDATE: payload={}",
                NotionLogSupport.truncate(notionJsonUtils.writeJson(body), MAX_LOG_BODY_CHARS));

        ResponseEntity<String> response = callApi(teamId,
                () -> notionRestClient.patch("/v1/pages/" + pageId,
                        notionTokenService.getAccessToken(teamId), body));

        JsonNode result = notionJsonUtils.parseJson(response);
        log.info("PAGE-UPDATE: responseStatus={}", response.getStatusCode().value());
        return result;
    }

    public JsonNode createPage(Long teamId, String databaseId, NotionCreatePageRequest payload) {
        if (payload == null || payload.title() == null || payload.title().isBlank()) {
            throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
        }
        log.info("PAGE-CREATE: incoming request={}", notionJsonUtils.writeJson(payload));

        JsonNode databaseNode = getDatabaseNode(teamId, databaseId);
        PropertyNames propertyNames = propertyResolver.resolvePropertyNames(databaseNode);
        List<String> availableProperties = propertyResolver.resolvePropertyKeys(databaseNode);

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode parent = objectMapper.createObjectNode();
        parent.put("database_id", databaseId);
        body.set("parent", parent);

        ObjectNode propertiesNode = objectMapper.createObjectNode();
        propertyResolver.requireProperty(propertyNames.title(), "title", availableProperties);
        ObjectNode titleNode = objectMapper.createObjectNode();
        titleNode.set("title", notionJsonUtils.toRichTextArray(payload.title()));
        propertiesNode.set(propertyNames.title(), titleNode);

        buildDateProperty(payload.date(), propertyNames, availableProperties, propertiesNode);

        if (payload.status() != null && !payload.status().isBlank()) {
            propertyResolver.requireProperty(propertyNames.status(), "status", availableProperties);
            ObjectNode statusValue = objectMapper.createObjectNode();
            statusValue.put("name", payload.status());
            ObjectNode statusNode = objectMapper.createObjectNode();
            statusNode.set("status", statusValue);
            propertiesNode.set(propertyNames.status(), statusNode);
        }

        body.set("properties", propertiesNode);

        if (payload.children() != null) {
            body.set("children", payload.children());
        }
        if (payload.icon() != null) {
            body.set("icon", payload.icon());
        }
        if (payload.cover() != null) {
            body.set("cover", payload.cover());
        }

        log.info("PAGE-CREATE: payload={}",
                NotionLogSupport.truncate(notionJsonUtils.writeJson(body), MAX_LOG_BODY_CHARS));

        ResponseEntity<String> response = callApi(teamId,
                () -> notionRestClient.post("/v1/pages",
                        notionTokenService.getAccessToken(teamId), body));
        return notionJsonUtils.parseJson(response);
    }

    private void buildDateProperty(NotionDateRange dateRange,
                                   PropertyNames propertyNames,
                                   List<String> availableProperties,
                                   ObjectNode propertiesNode) {
        if (dateRange == null) {
            return;
        }
        propertyResolver.requireProperty(propertyNames.date(), "date", availableProperties);

        String start = dateRange.start();
        String end = dateRange.end();

        if (start == null || start.isBlank() || !isIsoDateOrDateTime(start)) {
            throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
        }
        if (end != null && !end.isBlank() && !isIsoDateOrDateTime(end)) {
            throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
        }

        ObjectNode dateValue = objectMapper.createObjectNode();
        dateValue.put("start", start);
        if (end != null && !end.isBlank()) {
            dateValue.put("end", end);
        } else {
            dateValue.putNull("end");
        }
        ObjectNode dateNode = objectMapper.createObjectNode();
        dateNode.set("date", dateValue);
        propertiesNode.set(propertyNames.date(), dateNode);
    }

    private ResponseEntity<String> callApi(Long teamId, Supplier<ResponseEntity<String>> supplier) {
        ResponseEntity<String> response = notionTokenService.executeWithRefresh(teamId, supplier);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }
        return response;
    }

    private ObjectNode toObjectNode(JsonNode root) {
        if (root != null && root.isObject()) {
            return (ObjectNode) root;
        }
        ObjectNode aggregated = objectMapper.createObjectNode();
        if (root != null) {
            aggregated.set("blocks", root);
        } else {
            aggregated.putNull("blocks");
        }
        return aggregated;
    }

    private ArrayNode fetchChildDatabases(Long teamId, JsonNode root, NotionDatabaseQueryRequest request) {
        Set<String> childDatabaseIds = collectChildDatabaseIds(root);
        ArrayNode childDatabases = objectMapper.createArrayNode();
        for (String databaseId : childDatabaseIds) {
            JsonNode databaseQuery = notionDatabaseQueryService.queryDatabase(teamId, databaseId, request);
            childDatabases.add(databaseQuery);
        }
        return childDatabases;
    }

    private Set<String> collectChildDatabaseIds(JsonNode root) {
        Set<String> ids = new LinkedHashSet<>();
        if (root == null || root.isNull()) {
            return ids;
        }
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            return ids;
        }
        for (JsonNode block : results) {
            if (!"child_database".equals(block.path("type").asText(null))) {
                continue;
            }
            String databaseId = block.path("id").asText(null);
            if (databaseId == null || databaseId.isBlank()) {
                continue;
            }
            ids.add(databaseId);
        }
        return ids;
    }

    private JsonNode getDatabaseNode(Long teamId, String databaseId) {
        CachedDatabase cached = databaseCache.get(databaseId);
        if (cached != null) {
            Duration age = Duration.between(cached.cachedAt(), Instant.now());
            if (age.compareTo(DATABASE_SCHEMA_TTL) <= 0) {
                return cached.node();
            }
        }
        ResponseEntity<String> databaseResponse = callApi(teamId,
                () -> notionRestClient.get("/v1/databases/" + databaseId,
                        notionTokenService.getAccessToken(teamId)));
        JsonNode databaseNode = notionJsonUtils.parseJson(databaseResponse);
        databaseCache.put(databaseId, new CachedDatabase(databaseNode, Instant.now()));
        return databaseNode;
    }

    private void attachChildrenRecursively(Long teamId, JsonNode node, Integer pageSize, int depth) {
        if (depth >= MAX_CHILD_DEPTH) {
            return;
        }
        if (node == null || !node.has("results") || !node.get("results").isArray()) {
            return;
        }
        for (JsonNode block : node.get("results")) {
            if (!block.path("has_children").asBoolean(false)) {
                continue;
            }
            String blockId = block.path("id").asText(null);
            if (blockId == null || blockId.isBlank()) {
                continue;
            }
            ArrayNode allChildren = objectMapper.createArrayNode();
            String cursor = null;
            boolean hasMore;
            do {
                Map<String, Object> params = new HashMap<>();
                if (pageSize != null) {
                    params.put("page_size", pageSize);
                }
                if (cursor != null) {
                    params.put("start_cursor", cursor);
                }
                ResponseEntity<String> childResponse = callApi(teamId,
                        () -> notionRestClient.get("/v1/blocks/" + blockId + "/children",
                                notionTokenService.getAccessToken(teamId), params));
                JsonNode childRoot = notionJsonUtils.parseJson(childResponse);
                JsonNode results = childRoot.path("results");
                if (results.isArray()) {
                    results.forEach(allChildren::add);
                }
                hasMore = childRoot.path("has_more").asBoolean(false);
                cursor = childRoot.path("next_cursor").asText(null);
                if (hasMore && (cursor == null || cursor.isBlank())) {
                    hasMore = false;
                }
            } while (hasMore);
            if (block.isObject()) {
                ((ObjectNode) block).set("children", allChildren);
                attachChildrenRecursively(teamId, block, pageSize, depth + 1);
            }
        }
    }

    private boolean isEmptyUpdate(NotionPageUpdateRequest payload) {
        boolean titleEmpty = payload.title() == null || payload.title().isBlank();
        boolean statusEmpty = payload.status() == null || payload.status().isBlank();
        boolean dateEmpty = payload.date() == null;
        return titleEmpty && statusEmpty && dateEmpty;
    }
}
