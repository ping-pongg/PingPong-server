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
import pingpong.backend.domain.notion.dto.common.PageDateRange;
import pingpong.backend.domain.notion.dto.response.ChildDatabaseWithPagesResponse;
import pingpong.backend.domain.notion.dto.response.ChildPageSummary;
import pingpong.backend.domain.notion.dto.response.DatabaseWithPagesResponse;
import pingpong.backend.domain.notion.dto.response.PageSummary;
import pingpong.backend.domain.notion.util.NotionJsonUtils;
import pingpong.backend.domain.notion.util.NotionPropertyExtractor;
import pingpong.backend.global.exception.CustomException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class NotionDatabaseQueryService {

    private static final Logger log = LoggerFactory.getLogger(NotionDatabaseQueryService.class);
    private static final int DEFAULT_PAGE_SIZE = 100;

    private final NotionConnectionService notionConnectionService;
    private final NotionTokenService notionTokenService;
    private final NotionRestClient notionRestClient;
    private final NotionJsonUtils notionJsonUtils;
    private final ObjectMapper objectMapper;

    /**
     * 팀의 primary 데이터베이스를 조회하여 구조화된 응답 반환
     *
     * @param teamId 팀 ID
     * @return 데이터베이스 제목과 페이지 목록
     */
    public DatabaseWithPagesResponse queryPrimaryDatabase(Long teamId) {
        String databaseId = notionConnectionService.resolveConnectedDatabaseId(teamId);
        return queryDatabase(teamId, databaseId);
    }

    /**
     * 특정 데이터베이스를 조회하여 구조화된 응답 반환
     * (child database 조회에서도 사용)
     *
     * @param teamId 팀 ID
     * @param databaseId 조회할 데이터베이스 ID
     * @return 데이터베이스 제목과 페이지 목록
     */
    public DatabaseWithPagesResponse queryDatabase(Long teamId, String databaseId) {
        // 1. 데이터베이스 정보 조회하여 제목 추출
        String compactDatabaseId = compactNotionId(databaseId);
        ResponseEntity<String> databaseResponse = callApi(teamId,
                () -> notionRestClient.get("/v1/databases/" + compactDatabaseId, notionTokenService.getAccessToken(teamId)));
        JsonNode databaseNode = notionJsonUtils.parseJson(databaseResponse);

        String databaseTitle = NotionPropertyExtractor.extractTitleFromArray(databaseNode.get("title"));

        // 2. 데이터베이스 페이지 쿼리 (page_size: 100)
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page_size", DEFAULT_PAGE_SIZE);

        ResponseEntity<String> queryResponse = callApi(teamId,
                () -> notionRestClient.post("/v1/databases/" + compactDatabaseId + "/query",
                        notionTokenService.getAccessToken(teamId), body));
        JsonNode queryResult = notionJsonUtils.parseJson(queryResponse);

        // 3. 각 페이지에서 필요한 정보만 추출
        List<PageSummary> pages = new ArrayList<>();
        JsonNode results = queryResult.path("results");

        if (results.isArray()) {
            for (JsonNode pageNode : results) {
                String pageId = compactNotionId(pageNode.path("id").asText(null));
                if (pageId == null || pageId.isBlank()) {
                    continue;
                }

                JsonNode properties = pageNode.path("properties");
                String pageUrl = pageNode.path("url").asText(null);
                String title = NotionPropertyExtractor.extractTitle(properties);
                PageDateRange date = NotionPropertyExtractor.extractDateRange(properties);
                String status = NotionPropertyExtractor.extractStatus(properties);

                pages.add(new PageSummary(pageId, pageUrl, title, date, status));
            }
        }

        return new DatabaseWithPagesResponse(databaseTitle, pages);
    }

    /**
     * child database를 조회하여 구조화된 응답 반환 (date 제외)
     *
     * @param teamId 팀 ID
     * @param databaseId 조회할 데이터베이스 ID
     * @return 데이터베이스 제목과 페이지 목록
     */
    public ChildDatabaseWithPagesResponse queryChildDatabase(Long teamId, String databaseId) {
        String compactDatabaseId = compactNotionId(databaseId);
        ResponseEntity<String> databaseResponse = callApi(teamId,
                () -> notionRestClient.get("/v1/databases/" + compactDatabaseId, notionTokenService.getAccessToken(teamId)));
        JsonNode databaseNode = notionJsonUtils.parseJson(databaseResponse);
        String databaseTitle = NotionPropertyExtractor.extractTitleFromArray(databaseNode.get("title"));

        ObjectNode body = objectMapper.createObjectNode();
        body.put("page_size", DEFAULT_PAGE_SIZE);

        ResponseEntity<String> queryResponse = callApi(teamId,
                () -> notionRestClient.post("/v1/databases/" + compactDatabaseId + "/query",
                        notionTokenService.getAccessToken(teamId), body));
        JsonNode queryResult = notionJsonUtils.parseJson(queryResponse);

        List<ChildPageSummary> pages = new ArrayList<>();
        JsonNode results = queryResult.path("results");

        if (results.isArray()) {
            for (JsonNode pageNode : results) {
                String pageId = compactNotionId(pageNode.path("id").asText(null));
                if (pageId == null || pageId.isBlank()) {
                    continue;
                }

                JsonNode properties = pageNode.path("properties");
                String pageUrl = pageNode.path("url").asText(null);
                String title = NotionPropertyExtractor.extractTitle(properties);
                String status = NotionPropertyExtractor.extractStatus(properties);

                pages.add(new ChildPageSummary(pageId, pageUrl, title, status));
            }
        }

        return new ChildDatabaseWithPagesResponse(databaseTitle, pages);
    }

    /**
     * Notion API 호출을 토큰 갱신과 함께 실행
     */
    private ResponseEntity<String> callApi(Long teamId, Supplier<ResponseEntity<String>> supplier) {
        ResponseEntity<String> response = notionTokenService.executeWithRefresh(teamId, supplier);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }
        return response;
    }

    /**
     * Notion ID에서 대시(-)를 제거하여 compact 형태로 변환
     */
    private String compactNotionId(String notionId) {
        if (notionId == null) {
            return null;
        }
        return notionId.replace("-", "");
    }
}
