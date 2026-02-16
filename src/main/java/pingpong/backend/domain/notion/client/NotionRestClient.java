package pingpong.backend.domain.notion.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import pingpong.backend.domain.notion.config.NotionProperties;
import pingpong.backend.domain.notion.util.NotionLogSupport;

import java.util.Map;

@Component
@RequiredArgsConstructor
/**
 * Thin wrapper around Notion REST API calls.
 *
 * The actual Notion endpoint is determined by {@code path}. In this codebase, the following endpoints are used:
 * - {@code POST /v1/search}: {@code NotionFacade.resolveAndPersistDatabaseId}, {@code NotionConnectionApiService.listCandidateDatabases}
 * - {@code GET /v1/databases/{database_id}}: {@code NotionDatabaseQueryService}, {@code NotionPageService}, {@code NotionConnectionApiService.connectDatabase}
 * - {@code POST /v1/databases/{database_id}/query}: {@code NotionDatabaseQueryService.queryAll}
 * - {@code GET /v1/pages/{page_id}}: {@code NotionDatabaseQueryService.fetchPages}
 * - {@code POST /v1/pages}: {@code NotionPageService.createPage}
 * - {@code PATCH /v1/pages/{page_id}}: {@code NotionPageService.updatePage}
 * - {@code GET /v1/blocks/{block_id}/children}: {@code NotionPageService.getPageBlocks}, {@code NotionPageService.attachChildrenRecursively}
 * - {@code POST /v1/databases}: {@code NotionDatabaseCreateService.createDatabase}
 * - {@code GET /v1/data_sources/{data_source_id}}: {@code NotionDatabaseQueryService.queryPrimaryDatabase} (Notion data-source model)
 * - {@code POST /v1/data_sources/{data_source_id}/query}: {@code NotionDatabaseQueryService.queryAll} (Notion data-source model)
 */
public class NotionRestClient {

    private static final Logger log = LoggerFactory.getLogger(NotionRestClient.class);
    private static final int MAX_LOG_BODY_CHARS = 10 * 1024;

    private final RestTemplate notionRestTemplate;
    private final NotionProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Executes a Notion {@code POST} request.
     *
     * Endpoints used in this codebase include:
     * - {@code POST /v1/search}
     * - {@code POST /v1/pages}
     * - {@code POST /v1/databases}
     * - {@code POST /v1/databases/{database_id}/query}
     * - {@code POST /v1/data_sources/{data_source_id}/query}
     */
    public ResponseEntity<String> post(String path, String accessToken, Object body) {
        HttpEntity<Object> entity = new HttpEntity<>(body, buildBearerHeaders(accessToken));
        String url = buildUrl(properties.getApiBaseUrl(), path);
        return exchangeWithLogging(url, HttpMethod.POST, entity, body);
    }

    /**
     * Executes a Notion {@code PATCH} request.
     *
     * Endpoints used in this codebase include:
     * - {@code PATCH /v1/pages/{page_id}}
     */
    public ResponseEntity<String> patch(String path, String accessToken, Object body) {
        HttpEntity<Object> entity = new HttpEntity<>(body, buildBearerHeaders(accessToken));
        String url = buildUrl(properties.getApiBaseUrl(), path);
        return exchangeWithLogging(url, HttpMethod.PATCH, entity, body);
    }

    /**
     * Executes a Notion {@code GET} request.
     *
     * Endpoints used in this codebase include:
     * - {@code GET /v1/databases/{database_id}}
     * - {@code GET /v1/pages/{page_id}}
     * - {@code GET /v1/blocks/{block_id}/children}
     * - {@code GET /v1/data_sources/{data_source_id}}
     */
    public ResponseEntity<String> get(String path, String accessToken) {
        return get(path, accessToken, null);
    }

    /**
     * Executes a Notion {@code GET} request with optional query parameters.
     *
     * Endpoints used in this codebase include:
     * - {@code GET /v1/blocks/{block_id}/children?page_size=&start_cursor=}
     */
    public ResponseEntity<String> get(String path, String accessToken, Map<String, Object> queryParams) {
        HttpEntity<Void> entity = new HttpEntity<>(buildBearerHeaders(accessToken));
        String url = buildUrl(properties.getApiBaseUrl(), path);
        if (queryParams != null && !queryParams.isEmpty()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            queryParams.forEach(builder::queryParam);
            url = builder.toUriString();
        }
        return exchangeWithLogging(url, HttpMethod.GET, entity, null);
    }

    private ResponseEntity<String> exchangeWithLogging(String url, HttpMethod method, HttpEntity<?> entity, Object body) {
        HttpHeaders headers = entity.getHeaders();
        String requestBody = NotionLogSupport.toJsonString(objectMapper, body);
        String maskedAuth = NotionLogSupport.maskToken(headers.getFirst(HttpHeaders.AUTHORIZATION));
        String notionVersion = headers.getFirst("Notion-Version");
        String truncatedRequestBody = NotionLogSupport.truncate(requestBody, MAX_LOG_BODY_CHARS);
        try {
            ResponseEntity<String> response = notionRestTemplate.exchange(url, method, entity, String.class);
            String responseBody = response.getBody();
            String requestId = NotionLogSupport.extractRequestId(objectMapper, responseBody);
            String errorCode = NotionLogSupport.extractErrorCode(objectMapper, responseBody);
            String errorMessage = NotionLogSupport.extractErrorMessage(objectMapper, responseBody);
            log.info("Notion API {} {} | Notion-Version={} | Authorization={} | requestBody={} | status={} | requestId={} | errorCode={} | errorMessage={} | responseBody={}",
                    method, url, notionVersion, maskedAuth, truncatedRequestBody, response.getStatusCode().value(), requestId,
                    errorCode, errorMessage, NotionLogSupport.truncate(responseBody, MAX_LOG_BODY_CHARS));
            return response;
        } catch (RestClientException e) {
            log.warn("Notion API {} {} failed | Notion-Version={} | Authorization={} | requestBody={} | error={}",
                    method, url, notionVersion, maskedAuth, truncatedRequestBody, e.getMessage(), e);
            throw e;
        }
    }

    private HttpHeaders buildBearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Notion-Version", properties.getNotionVersion());
        return headers;
    }

    private String buildUrl(String baseUrl, String path) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl).path(path).build().toUriString();
    }
}
