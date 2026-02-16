package pingpong.backend.domain.notion.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;

public class NotionLogSupport {

    private NotionLogSupport() {
    }

    public static String truncate(String body, int maxChars) {
        if (body == null) {
            return null;
        }
        if (body.length() <= maxChars) {
            return body;
        }
        return body.substring(0, maxChars) + "...(truncated)";
    }

    public static String maskToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        if (authHeader.toLowerCase().startsWith("basic ")) {
            return "Basic ****";
        }
        String token = authHeader.replaceFirst("(?i)^Bearer\\s+", "");
        int keep = Math.min(4, token.length());
        String suffix = keep == 0 ? "" : token.substring(token.length() - keep);
        return "Bearer ****" + suffix;
    }

    public static String toJsonString(ObjectMapper objectMapper, Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof String str) {
            return str;
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return String.valueOf(body);
        }
    }

    public static String extractRequestId(ObjectMapper objectMapper, String responseBodyJson) {
        if (responseBodyJson == null || responseBodyJson.isBlank()) {
            return "unparseable";
        }
        try {
            JsonNode node = objectMapper.readTree(responseBodyJson);
            String requestId = node.path("request_id").asText(null);
            return requestId == null || requestId.isBlank() ? "unparseable" : requestId;
        } catch (Exception e) {
            return "unparseable";
        }
    }

    public static String extractRequestId(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String requestId = headers.getFirst("request-id");
        if (requestId == null) {
            requestId = headers.getFirst("x-request-id");
        }
        return requestId;
    }

    public static String extractErrorCode(ObjectMapper objectMapper, String responseBodyJson) {
        if (responseBodyJson == null || responseBodyJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(responseBodyJson);
            return node.path("code").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractErrorMessage(ObjectMapper objectMapper, String responseBodyJson) {
        if (responseBodyJson == null || responseBodyJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(responseBodyJson);
            return node.path("message").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}
