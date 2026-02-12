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
import pingpong.backend.domain.notion.config.NotionProperties;
import pingpong.backend.domain.notion.util.NotionJsonUtils;
import pingpong.backend.global.exception.CustomException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class NotionDatabaseQueryService {

    private static final Logger log = LoggerFactory.getLogger(NotionDatabaseQueryService.class);
    private static final String VERSION_2025_09_03 = "2025-09-03";
    private static final int DEFAULT_PAGE_SIZE = 100;

    private final NotionConnectionService notionConnectionService;
    private final NotionTokenService notionTokenService;
    private final NotionRestClient notionRestClient;
    private final NotionProperties properties;
    private final NotionJsonUtils notionJsonUtils;
    private final ObjectMapper objectMapper;

    public JsonNode queryPrimaryDatabase(Long teamId) {
        String databaseId = notionConnectionService.resolveConnectedDatabaseId(teamId);

        ResponseEntity<String> databaseResponse = callApi(teamId,
                () -> notionRestClient.get("/v1/databases/" + databaseId, notionTokenService.getAccessToken(teamId)));
        JsonNode databaseNode = notionJsonUtils.parseJson(databaseResponse);

        String dataSourceId = resolveDataSourceId(teamId, databaseNode);
        boolean isDataSourceModel = VERSION_2025_09_03.equals(properties.getNotionVersion());
        if (isDataSourceModel && (dataSourceId == null || dataSourceId.isBlank())) {
            log.warn("FULL-FLOW: dataSourceId missing for data-source model; databaseId={}", databaseId);
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }

        JsonNode dataSourceNode = objectMapper.createObjectNode();
        if (dataSourceId != null && !dataSourceId.isBlank()) {
            ResponseEntity<String> dataSourceResponse = callApi(teamId,
                    () -> notionRestClient.get("/v1/data_sources/" + dataSourceId, notionTokenService.getAccessToken(teamId)));
            dataSourceNode = notionJsonUtils.parseJson(dataSourceResponse);
        }

        String queryPath;
        if (isDataSourceModel && dataSourceId != null && !dataSourceId.isBlank()) {
            queryPath = "/v1/data_sources/" + dataSourceId + "/query";
        } else {
            queryPath = "/v1/databases/" + databaseId + "/query";
        }
        log.info("FULL-FLOW: Notion query | path={} | databaseId={} | dataSourceId={}", queryPath, databaseId, dataSourceId);

        ObjectNode queryNode = queryAll(teamId, queryPath);
        ArrayNode pagesNode = fetchPages(teamId, queryNode);

        ObjectNode aggregated = objectMapper.createObjectNode();
        aggregated.set("database", databaseNode);
        aggregated.set("data_source", dataSourceNode);
        aggregated.set("query_result", queryNode);
        aggregated.set("pages", pagesNode);
        return aggregated;
    }

    public JsonNode queryDatabase(Long teamId, String databaseId) {
        ResponseEntity<String> databaseResponse = callApi(teamId,
                () -> notionRestClient.get("/v1/databases/" + databaseId, notionTokenService.getAccessToken(teamId)));
        JsonNode databaseNode = notionJsonUtils.parseJson(databaseResponse);

        ObjectNode queryNode = queryAll(teamId, "/v1/databases/" + databaseId + "/query");
        ArrayNode pagesNode = fetchPages(teamId, queryNode);

        ObjectNode aggregated = objectMapper.createObjectNode();
        aggregated.set("database", databaseNode);
        aggregated.set("query_result", queryNode);
        aggregated.set("pages", pagesNode);
        return aggregated;
    }

    private ObjectNode queryAll(Long teamId, String queryPath) {
        ArrayNode allResults = objectMapper.createArrayNode();
        String cursor = null;
        boolean hasMore;
        ObjectNode baseNode = null;

        do {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("page_size", DEFAULT_PAGE_SIZE);
            if (cursor != null && !cursor.isBlank()) {
                body.put("start_cursor", cursor);
            }

            ResponseEntity<String> queryResponse = callApi(teamId,
                    () -> notionRestClient.post(queryPath, notionTokenService.getAccessToken(teamId), body));
            JsonNode queryNode = notionJsonUtils.parseJson(queryResponse);
            if (queryNode == null || queryNode.isNull()) {
                break;
            }

            if (baseNode == null) {
                baseNode = queryNode.isObject() ? ((ObjectNode) queryNode).deepCopy() : objectMapper.createObjectNode();
            }

            JsonNode results = queryNode.path("results");
            if (results.isArray()) {
                results.forEach(allResults::add);
            }

            hasMore = queryNode.path("has_more").asBoolean(false);
            cursor = queryNode.path("next_cursor").asText(null);
            if (hasMore && (cursor == null || cursor.isBlank())) {
                hasMore = false;
            }
        } while (hasMore);

        if (baseNode == null) {
            baseNode = objectMapper.createObjectNode();
        }
        baseNode.set("results", allResults);
        baseNode.put("has_more", false);
        baseNode.putNull("next_cursor");
        return baseNode;
    }

    private ResponseEntity<String> callApi(Long teamId, Supplier<ResponseEntity<String>> supplier) {
        ResponseEntity<String> response = notionTokenService.executeWithRefresh(teamId, supplier);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }
        return response;
    }

    private ArrayNode fetchPages(Long teamId, ObjectNode queryNode) {
        ArrayNode pagesNode = objectMapper.createArrayNode();

        Set<String> pageIds = new LinkedHashSet<>();
        JsonNode results = queryNode.path("results");
        if (results.isArray()) {
            for (JsonNode result : results) {
                String pageId = result.path("id").asText(null);
                if (pageId == null || pageId.isBlank()) {
                    continue;
                }
                pageIds.add(pageId);
            }
        }

        for (String pageId : pageIds) {
            ResponseEntity<String> pageResponse = callApi(teamId,
                    () -> notionRestClient.get("/v1/pages/" + pageId, notionTokenService.getAccessToken(teamId)));
            pagesNode.add(notionJsonUtils.parseJson(pageResponse));
        }

        return pagesNode;
    }

    private String resolveDataSourceId(Long teamId, JsonNode databaseNode) {
        String dataSourceId = notionTokenService.getNotionOrThrow(teamId).getDataSourceId();
        if (dataSourceId != null && !dataSourceId.isBlank()) {
            return dataSourceId;
        }
        return notionJsonUtils.extractDataSourceId(databaseNode);
    }
}

