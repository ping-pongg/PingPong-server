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
import pingpong.backend.domain.notion.dto.response.DatabaseCreatedResponse;
import pingpong.backend.domain.notion.util.NotionJsonUtils;
import pingpong.backend.domain.notion.util.NotionLogSupport;
import pingpong.backend.domain.notion.util.NotionPropertyExtractor;
import pingpong.backend.global.exception.CustomException;

import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class NotionDatabaseCreateService {

    private static final Logger log = LoggerFactory.getLogger(NotionDatabaseCreateService.class);
    private static final int MAX_LOG_BODY_CHARS = 10 * 1024;

    private final NotionTokenService notionTokenService;
    private final NotionRestClient notionRestClient;
    private final NotionJsonUtils notionJsonUtils;
    private final ObjectMapper objectMapper;

    /**
     * 페이지 내에 고정된 구조의 데이터베이스 생성
     * 요구사항에 따라 "API Status Overview" 데이터베이스를 생성
     *
     * @param teamId 팀 ID
     * @param parentPageId 부모 페이지 ID
     * @return 생성된 데이터베이스 정보 (id, title, url)
     */
    public DatabaseCreatedResponse createDatabase(Long teamId, String parentPageId) {
        String normalizedParentPageId = compactNotionId(parentPageId);
        log.info("DB-CREATE: Creating fixed structure database in page={}", normalizedParentPageId);

        // 고정된 body 구조 생성
        ObjectNode body = objectMapper.createObjectNode();

        // parent
        ObjectNode parent = objectMapper.createObjectNode();
        parent.put("page_id", normalizedParentPageId);
        body.set("parent", parent);

        // title (고정: "API Status Overview")
        body.set("title", notionJsonUtils.toRichTextArray("API Status Overview"));

        // is_inline (고정: true)
        body.put("is_inline", true);

        // properties (고정 구조)
        ObjectNode propertiesNode = objectMapper.createObjectNode();

        // "Status" property: select type
        ObjectNode statusProperty = objectMapper.createObjectNode();
        statusProperty.put("type", "select");
        ObjectNode selectConfig = objectMapper.createObjectNode();
        selectConfig.set("options", objectMapper.createArrayNode());
        statusProperty.set("select", selectConfig);
        propertiesNode.set("Status", statusProperty);

        // "API List" property: title type
        ObjectNode apiListProperty = objectMapper.createObjectNode();
        apiListProperty.put("type", "title");
        apiListProperty.set("title", objectMapper.createObjectNode());
        propertiesNode.set("API List", apiListProperty);

        body.set("properties", propertiesNode);

        log.info("DB-CREATE: payload={}",
                NotionLogSupport.truncate(notionJsonUtils.writeJson(body), MAX_LOG_BODY_CHARS));

        ResponseEntity<String> response = callApi(teamId,
                () -> notionRestClient.post("/v1/databases", notionTokenService.getAccessToken(teamId), body));

        JsonNode result = notionJsonUtils.parseJson(response);
        log.info("DB-CREATE: responseStatus={}", response.getStatusCode().value());

        // 응답에서 필요한 정보만 추출
        String databaseId = compactNotionId(result.path("id").asText(null));
        String databaseTitle = NotionPropertyExtractor.extractTitleFromArray(result.get("title"));
        String databaseUrl = result.path("url").asText(null);

        return new DatabaseCreatedResponse(databaseId, databaseTitle, databaseUrl);
    }

    /**
     * Notion 블록(데이터베이스 등)을 archived 상태로 변경 (삭제)
     *
     * @param teamId  팀 ID
     * @param blockId 아카이브할 블록 ID
     */
    public void archiveBlock(Long teamId, String blockId) {
        String normalizedBlockId = compactNotionId(blockId);
        log.info("BLOCK-ARCHIVE: Archiving block={}", normalizedBlockId);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("archived", true);

        callApi(teamId,
                () -> notionRestClient.patch("/v1/blocks/" + normalizedBlockId, notionTokenService.getAccessToken(teamId), body));
    }

    /**
     * 데이터베이스 행(페이지)의 Status(select) 값 수정
     *
     * @param teamId    팀 ID
     * @param pageId    수정할 페이지 ID
     * @param newStatus 새 Status 값 ("Backend" / "Frontend" / "Complete")
     */
    public void updateRowStatus(Long teamId, String pageId, String newStatus) {
        String normalizedPageId = compactNotionId(pageId);
        log.info("DB-ROW-UPDATE: pageId={} newStatus={}", normalizedPageId, newStatus);

        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode selectNode = objectMapper.createObjectNode();
        selectNode.put("name", newStatus);
        ObjectNode statusProperty = objectMapper.createObjectNode();
        statusProperty.set("select", selectNode);
        properties.set("Status", statusProperty);

        ObjectNode body = objectMapper.createObjectNode();
        body.set("properties", properties);

        callApi(teamId,
                () -> notionRestClient.patch("/v1/pages/" + normalizedPageId,
                        notionTokenService.getAccessToken(teamId), body));
    }

    /**
     * 데이터베이스에 행(페이지) 추가
     *
     * @param teamId       팀 ID
     * @param databaseId   대상 데이터베이스 ID
     * @param apiListValue "API List" title 컬럼에 입력할 값 (예: "GET /api/v1/users")
     */
    public void addRowToDatabase(Long teamId, String databaseId, String apiListValue) {
        String normalizedDatabaseId = compactNotionId(databaseId);
        log.info("DB-ROW-ADD: Adding row to database={}, apiList={}", normalizedDatabaseId, apiListValue);

        ObjectNode body = objectMapper.createObjectNode();

        ObjectNode parent = objectMapper.createObjectNode();
        parent.put("database_id", normalizedDatabaseId);
        body.set("parent", parent);

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode apiListProperty = objectMapper.createObjectNode();
        apiListProperty.set("title", notionJsonUtils.toRichTextArray(apiListValue));
        properties.set("API List", apiListProperty);

        ObjectNode statusProperty = objectMapper.createObjectNode();
        ObjectNode selectNode = objectMapper.createObjectNode();
        selectNode.put("name", "Backend");
        statusProperty.set("select", selectNode);
        properties.set("Status", statusProperty);

        body.set("properties", properties);

        callApi(teamId,
                () -> notionRestClient.post("/v1/pages", notionTokenService.getAccessToken(teamId), body));
    }

    private ResponseEntity<String> callApi(Long teamId, Supplier<ResponseEntity<String>> supplier) {
        ResponseEntity<String> response = notionTokenService.executeWithRefresh(teamId, supplier);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }
        return response;
    }


    private String compactNotionId(String notionId) {
        return notionId == null ? null : notionId.replace("-", "");
    }
}
