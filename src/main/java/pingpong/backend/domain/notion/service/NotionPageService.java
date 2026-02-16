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
import pingpong.backend.domain.notion.dto.request.NotionCreatePageRequest;
import pingpong.backend.domain.notion.dto.common.NotionDateRange;
import pingpong.backend.domain.notion.dto.request.NotionPageUpdateRequest;
import pingpong.backend.domain.notion.dto.response.DatabaseWithPagesResponse;
import pingpong.backend.domain.notion.dto.common.PageDateRange;
import pingpong.backend.domain.notion.dto.response.PageDetailResponse;
import pingpong.backend.domain.notion.service.NotionPropertyResolver.PropertyNames;
import pingpong.backend.domain.notion.util.NotionJsonUtils;
import pingpong.backend.domain.notion.util.NotionLogSupport;
import pingpong.backend.domain.notion.util.NotionPropertyExtractor;
import pingpong.backend.global.exception.CustomException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static pingpong.backend.domain.notion.util.NotionDateValidator.isIsoDateOrDateTime;

@Service
@RequiredArgsConstructor
public class NotionPageService {

    private static final Logger log = LoggerFactory.getLogger(NotionPageService.class);
    private static final int MAX_LOG_BODY_CHARS = 10 * 1024;
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

    /**
     * 페이지 상세 정보를 조회 (속성, 본문 내용, 자식 데이터베이스)
     *
     * @param teamId 팀 ID
     * @param pageId 페이지 ID
     * @return 페이지 상세 정보
     */
    public PageDetailResponse getPageBlocks(Long teamId, String pageId) {
        String normalizedPageId = compactNotionId(pageId);

        // 1. 페이지 속성 조회
        ResponseEntity<String> pageResponse = callApi(teamId,
                () -> notionRestClient.get("/v1/pages/" + normalizedPageId,
                        notionTokenService.getAccessToken(teamId)));
        JsonNode pageNode = notionJsonUtils.parseJson(pageResponse);

        // 2. 페이지 속성 추출
        JsonNode properties = pageNode.path("properties");
        String title = NotionPropertyExtractor.extractTitle(properties);
        PageDateRange date = NotionPropertyExtractor.extractDateRange(properties);
        String status = NotionPropertyExtractor.extractStatus(properties);

        // 3. 페이지 블록(본문) 조회
        ResponseEntity<String> blocksResponse = callApi(teamId,
                () -> notionRestClient.get("/v1/blocks/" + normalizedPageId + "/children",
                        notionTokenService.getAccessToken(teamId)));
        JsonNode blocksRoot = notionJsonUtils.parseJson(blocksResponse);

        // 4. paragraph 텍스트 추출
        JsonNode results = blocksRoot.path("results");
        String pageContent = NotionPropertyExtractor.extractParagraphText(results);

        // 5. child_database 블록들 처리
        List<DatabaseWithPagesResponse> childDatabases = fetchChildDatabases(teamId, blocksRoot);

        return new PageDetailResponse(
                normalizedPageId,
                title,
                date,
                status,
                pageContent,
                childDatabases
        );
    }

    /**
     * 페이지를 수정하고 상세 정보를 반환
     *
     * @param teamId 팀 ID
     * @param pageId 페이지 ID
     * @param payload 페이지 수정 요청
     * @return 수정된 페이지의 상세 정보
     */
    public PageDetailResponse updatePage(Long teamId, String pageId, NotionPageUpdateRequest payload) {
        String databaseId = notionConnectionService.resolveConnectedDatabaseId(teamId);
        String normalizedPageId = compactNotionId(pageId);
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
                () -> notionRestClient.patch("/v1/pages/" + normalizedPageId,
                        notionTokenService.getAccessToken(teamId), body));

        JsonNode result = notionJsonUtils.parseJson(response);
        log.info("PAGE-UPDATE: responseStatus={}", response.getStatusCode().value());

        // 수정된 페이지의 상세 정보 조회
        return getPageBlocks(teamId, normalizedPageId);
    }

    /**
     * 데이터베이스에 새 페이지를 생성하고 상세 정보를 반환
     *
     * @param teamId 팀 ID
     * @param databaseId 데이터베이스 ID
     * @param payload 페이지 생성 요청
     * @return 생성된 페이지의 상세 정보
     */
    public PageDetailResponse createPage(Long teamId, String databaseId, NotionCreatePageRequest payload) {
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

        log.info("PAGE-CREATE: payload={}",
                NotionLogSupport.truncate(notionJsonUtils.writeJson(body), MAX_LOG_BODY_CHARS));

        ResponseEntity<String> response = callApi(teamId,
                () -> notionRestClient.post("/v1/pages",
                        notionTokenService.getAccessToken(teamId), body));

        JsonNode result = notionJsonUtils.parseJson(response);
        log.info("PAGE-CREATE: responseStatus={}", response.getStatusCode().value());

        // 생성된 페이지의 ID를 추출하여 상세 정보 조회
        String createdPageId = result.path("id").asText(null);
        if (createdPageId == null || createdPageId.isBlank()) {
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }

        return getPageBlocks(teamId, createdPageId);
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

    /**
     * 블록 결과에서 child_database를 찾아 조회
     */
    private List<DatabaseWithPagesResponse> fetchChildDatabases(Long teamId, JsonNode root) {
        Set<String> childDatabaseIds = collectChildDatabaseIds(root);
        List<DatabaseWithPagesResponse> childDatabases = new ArrayList<>();
        for (String databaseId : childDatabaseIds) {
            DatabaseWithPagesResponse databaseQuery = notionDatabaseQueryService.queryDatabase(teamId, databaseId);
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


    private boolean isEmptyUpdate(NotionPageUpdateRequest payload) {
        boolean titleEmpty = payload.title() == null || payload.title().isBlank();
        boolean statusEmpty = payload.status() == null || payload.status().isBlank();
        boolean dateEmpty = payload.date() == null;
        return titleEmpty && statusEmpty && dateEmpty;
    }

    private String compactNotionId(String notionId) {
        return notionId == null ? null : notionId.replace("-", "");
    }
}
