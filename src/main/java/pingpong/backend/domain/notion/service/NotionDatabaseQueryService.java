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
import pingpong.backend.domain.notion.dto.NotionDatabaseFullQueryRequest;
import pingpong.backend.domain.notion.dto.NotionTimestampFilterRequest;
import pingpong.backend.domain.notion.dto.NotionTimestampSortRequest;
import pingpong.backend.domain.notion.enums.NotionQueryLogic;
import pingpong.backend.domain.notion.util.NotionJsonUtils;
import pingpong.backend.global.exception.CustomException;

import java.util.List;
import java.util.function.Supplier;

import static pingpong.backend.domain.notion.util.NotionDateValidator.isIsoDateOrDateTime;

@Service
@RequiredArgsConstructor
public class NotionDatabaseQueryService {

    private static final Logger log = LoggerFactory.getLogger(NotionDatabaseQueryService.class);
    private static final String VERSION_2025_09_03 = "2025-09-03";

    private static final String[] FORBIDDEN_KEYS = {
            "property", "title", "status", "select", "date", "filter", "sorts", "equals"
    };

    private final NotionConnectionService notionConnectionService;
    private final NotionTokenService notionTokenService;
    private final NotionRestClient notionRestClient;
    private final NotionProperties properties;
    private final NotionJsonUtils notionJsonUtils;
    private final ObjectMapper objectMapper;

    public JsonNode queryPrimaryDatabase(Long teamId, JsonNode request) {
        String databaseId = notionConnectionService.resolveConnectedDatabaseId(teamId);

        NotionDatabaseFullQueryRequest queryRequest = parseRestrictedRequest(request);

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

        ObjectNode queryBody = buildQueryBody(queryRequest);

        String queryPath;
        if (isDataSourceModel && dataSourceId != null && !dataSourceId.isBlank()) {
            queryPath = "/v1/data_sources/" + dataSourceId + "/query";
        } else {
            queryPath = "/v1/databases/" + databaseId + "/query";
        }
        log.info("FULL-FLOW: Notion query | path={} | databaseId={} | dataSourceId={}", queryPath, databaseId, dataSourceId);

        ResponseEntity<String> queryResponse = callApi(teamId,
                () -> notionRestClient.post(queryPath, notionTokenService.getAccessToken(teamId), queryBody));
        JsonNode queryNode = notionJsonUtils.parseJson(queryResponse);

        boolean includePages = queryRequest == null || queryRequest.includePages() == null || queryRequest.includePages();
        ArrayNode pagesNode = fetchPages(teamId, queryNode, includePages);

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

        ObjectNode queryBody = objectMapper.createObjectNode();

        ResponseEntity<String> queryResponse = callApi(teamId,
                () -> notionRestClient.post("/v1/databases/" + databaseId + "/query", notionTokenService.getAccessToken(teamId), queryBody));
        JsonNode queryNode = notionJsonUtils.parseJson(queryResponse);

        ArrayNode pagesNode = fetchPages(teamId, queryNode, true);

        ObjectNode aggregated = objectMapper.createObjectNode();
        aggregated.set("database", databaseNode);
        aggregated.set("query_result", queryNode);
        aggregated.set("pages", pagesNode);
        return aggregated;
    }

    private ResponseEntity<String> callApi(Long teamId, Supplier<ResponseEntity<String>> supplier) {
        ResponseEntity<String> response = notionTokenService.executeWithRefresh(teamId, supplier);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }
        return response;
    }

    private ArrayNode fetchPages(Long teamId, JsonNode queryNode, boolean includePages) {
        ArrayNode pagesNode = objectMapper.createArrayNode();
        if (!includePages) {
            return pagesNode;
        }
        JsonNode results = queryNode.path("results");
        if (results.isArray()) {
            for (JsonNode result : results) {
                String pageId = result.path("id").asText(null);
                if (pageId == null || pageId.isBlank()) {
                    continue;
                }
                ResponseEntity<String> pageResponse = callApi(teamId,
                        () -> notionRestClient.get("/v1/pages/" + pageId, notionTokenService.getAccessToken(teamId)));
                pagesNode.add(notionJsonUtils.parseJson(pageResponse));
            }
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

    private ObjectNode buildQueryBody(NotionDatabaseFullQueryRequest queryRequest) {
        ObjectNode queryBody = objectMapper.createObjectNode();
        if (queryRequest == null) {
            return queryBody;
        }
        if (queryRequest.timestampFilters() != null && !queryRequest.timestampFilters().isEmpty()) {
            queryBody.set("filter", buildTimestampFilter(queryRequest));
        }
        if (queryRequest.timestampSorts() != null && !queryRequest.timestampSorts().isEmpty()) {
            queryBody.set("sorts", buildTimestampSorts(queryRequest.timestampSorts()));
        }
        if (queryRequest.pageSize() != null) {
            queryBody.put("page_size", queryRequest.pageSize());
        }
        if (queryRequest.startCursor() != null && !queryRequest.startCursor().isBlank()) {
            queryBody.put("start_cursor", queryRequest.startCursor());
        }
        return queryBody;
    }

    private NotionDatabaseFullQueryRequest parseRestrictedRequest(JsonNode request) {
        if (request == null || request.isNull()) {
            return null;
        }
        if (!request.isObject()) {
            throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
        }
        validateNoForbiddenKeys(request);
        validateAllowedStructure(request);
        NotionDatabaseFullQueryRequest parsed;
        try {
            parsed = objectMapper.treeToValue(request, NotionDatabaseFullQueryRequest.class);
        } catch (Exception e) {
            throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
        }
        validateRestrictedRequest(parsed);
        return parsed;
    }

    private void validateRestrictedRequest(NotionDatabaseFullQueryRequest request) {
        if (request == null) {
            return;
        }
        Integer pageSize = request.pageSize();
        if (pageSize != null && (pageSize < 1 || pageSize > 100)) {
            throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
        }
        List<NotionTimestampFilterRequest> filters = request.timestampFilters();
        if (filters != null) {
            for (NotionTimestampFilterRequest filter : filters) {
                if (filter == null || filter.timestamp() == null || filter.operator() == null
                        || filter.value() == null || filter.value().isBlank()) {
                    throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
                }
                if (!isIsoDateOrDateTime(filter.value())) {
                    throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
                }
            }
        }
        List<NotionTimestampSortRequest> sorts = request.timestampSorts();
        if (sorts != null) {
            for (NotionTimestampSortRequest sort : sorts) {
                if (sort == null || sort.timestamp() == null || sort.direction() == null) {
                    throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
                }
            }
        }
    }

    private ObjectNode buildTimestampFilter(NotionDatabaseFullQueryRequest request) {
        ArrayNode filters = objectMapper.createArrayNode();
        for (NotionTimestampFilterRequest filter : request.timestampFilters()) {
            ObjectNode node = objectMapper.createObjectNode();
            String timestampKey = filter.timestamp().getValue();
            node.put("timestamp", timestampKey);
            ObjectNode opNode = objectMapper.createObjectNode();
            opNode.put(filter.operator().getValue(), filter.value());
            node.set(timestampKey, opNode);
            filters.add(node);
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        String logic = request.logic() == NotionQueryLogic.OR ? "or" : "and";
        wrapper.set(logic, filters);
        return wrapper;
    }

    private ArrayNode buildTimestampSorts(List<NotionTimestampSortRequest> sorts) {
        ArrayNode array = objectMapper.createArrayNode();
        for (NotionTimestampSortRequest sort : sorts) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("timestamp", sort.timestamp().getValue());
            node.put("direction", sort.direction().getValue());
            array.add(node);
        }
        return array;
    }

    private void validateNoForbiddenKeys(JsonNode node) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(field -> {
                for (String forbidden : FORBIDDEN_KEYS) {
                    if (forbidden.equals(field)) {
                        throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
                    }
                }
                validateNoForbiddenKeys(node.get(field));
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                validateNoForbiddenKeys(child);
            }
        }
    }

    private void validateAllowedStructure(JsonNode root) {
        root.fieldNames().forEachRemaining(field -> {
            if (!field.equals("logic")
                    && !field.equals("timestampFilters")
                    && !field.equals("timestampSorts")
                    && !field.equals("pageSize")
                    && !field.equals("startCursor")
                    && !field.equals("includePages")) {
                throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
            }
        });

        JsonNode filters = root.get("timestampFilters");
        if (filters != null) {
            if (!filters.isArray()) {
                throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
            }
            for (JsonNode filter : filters) {
                if (!filter.isObject()) {
                    throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
                }
                filter.fieldNames().forEachRemaining(field -> {
                    if (!field.equals("timestamp") && !field.equals("operator") && !field.equals("value")) {
                        throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
                    }
                });
            }
        }

        JsonNode sorts = root.get("timestampSorts");
        if (sorts != null) {
            if (!sorts.isArray()) {
                throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
            }
            for (JsonNode sort : sorts) {
                if (!sort.isObject()) {
                    throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
                }
                sort.fieldNames().forEachRemaining(field -> {
                    if (!field.equals("timestamp") && !field.equals("direction")) {
                        throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
                    }
                });
            }
        }

        JsonNode includePages = root.get("includePages");
        if (includePages != null && !includePages.isBoolean()) {
            throw new CustomException(NotionErrorCode.NOTION_INVALID_QUERY);
        }
    }
}
