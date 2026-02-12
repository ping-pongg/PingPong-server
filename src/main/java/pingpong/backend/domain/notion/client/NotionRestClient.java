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
public class NotionRestClient {

    private static final Logger log = LoggerFactory.getLogger(NotionRestClient.class);
    private static final int MAX_LOG_BODY_CHARS = 10 * 1024;

    private final RestTemplate notionRestTemplate;
    private final NotionProperties properties;
    private final ObjectMapper objectMapper;

    public ResponseEntity<String> post(String path, String accessToken, Object body) {
        HttpEntity<Object> entity = new HttpEntity<>(body, buildBearerHeaders(accessToken));
        String url = buildUrl(properties.getApiBaseUrl(), path);
        return exchangeWithLogging(url, HttpMethod.POST, entity, body);
    }

    public ResponseEntity<String> patch(String path, String accessToken, Object body) {
        HttpEntity<Object> entity = new HttpEntity<>(body, buildBearerHeaders(accessToken));
        String url = buildUrl(properties.getApiBaseUrl(), path);
        return exchangeWithLogging(url, HttpMethod.PATCH, entity, body);
    }

    public ResponseEntity<String> get(String path, String accessToken) {
        return get(path, accessToken, null);
    }

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
        return baseUrl + path;
    }
}
