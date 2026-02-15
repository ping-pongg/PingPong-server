package pingpong.backend.domain.notion.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import pingpong.backend.domain.notion.config.NotionProperties;
import pingpong.backend.domain.notion.dto.NotionOAuthTokenResponse;
import pingpong.backend.domain.notion.util.NotionLogSupport;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotionOauthClient {

    private static final Logger log = LoggerFactory.getLogger(NotionOauthClient.class);
    private static final int MAX_LOG_BODY_CHARS = 10 * 1024;

    private final RestTemplate notionRestTemplate;
    private final NotionProperties properties;
    private final ObjectMapper objectMapper;

    public NotionOAuthTokenResponse exchangeAuthorizationCode(String code, String redirectUri) {
        Map<String, Object> body = Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", redirectUri
        );
        return callTokenEndpoint(body);
    }

    public NotionOAuthTokenResponse refreshToken(String refreshToken) {
        Map<String, Object> body = Map.of(
                "grant_type", "refresh_token",
                "refresh_token", refreshToken
        );
        return callTokenEndpoint(body);
    }

    private NotionOAuthTokenResponse callTokenEndpoint(Map<String, Object> body) {
        HttpHeaders headers = buildBasicHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = buildUrl(properties.getApiBaseUrl(), properties.getOauthTokenPath());
        String requestBody = NotionLogSupport.truncate(NotionLogSupport.toJsonString(objectMapper, body), MAX_LOG_BODY_CHARS);
        String maskedAuth = NotionLogSupport.maskToken(headers.getFirst(HttpHeaders.AUTHORIZATION));
        try {
            ResponseEntity<NotionOAuthTokenResponse> response = notionRestTemplate.postForEntity(
                    url,
                    entity,
                    NotionOAuthTokenResponse.class
            );
            String responseBody = NotionLogSupport.truncate(
                    NotionLogSupport.toJsonString(objectMapper, response.getBody()),
                    MAX_LOG_BODY_CHARS
            );
            String requestId = NotionLogSupport.extractRequestId(response.getHeaders());
            log.info("Notion Auth POST {} | Authorization={} | requestBody={} | status={} | requestId={} | responseBody={}",
                    url, maskedAuth, requestBody, response.getStatusCode().value(), requestId, responseBody);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Notion OAuth token request failed");
            }
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("Notion Auth POST {} failed | Authorization={} | requestBody={} | error={}",
                    url, maskedAuth, requestBody, e.getMessage());
            throw e;
        }
    }

    private HttpHeaders buildBasicHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(properties.getClientId(), properties.getClientSecret());
        return headers;
    }

    private String buildUrl(String baseUrl, String path) {
        return baseUrl + path;
    }
}
