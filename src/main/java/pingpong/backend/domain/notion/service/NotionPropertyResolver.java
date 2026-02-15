package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import pingpong.backend.global.exception.ApiErrorCode;
import pingpong.backend.global.exception.CustomException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class NotionPropertyResolver {

    private static final class DynamicErrorCode implements ApiErrorCode {
        private final String errorCode;
        private final String message;
        private final HttpStatus status;

        private DynamicErrorCode(String message) {
            this.errorCode = "NOTION400";
            this.message = message;
            this.status = HttpStatus.BAD_REQUEST;
        }

        @Override
        public String getErrorCode() {
            return errorCode;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public HttpStatus getStatus() {
            return status;
        }
    }

    public record PropertyNames(String title, String date, String status) {
    }

    public PropertyNames resolvePropertyNames(JsonNode databaseNode) {
        JsonNode props = databaseNode.path("properties");
        String title = null;
        String date = null;
        String status = null;
        if (props.isObject()) {
            Iterator<String> names = props.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                JsonNode prop = props.get(name);
                String type = prop.path("type").asText(null);
                if ("title".equals(type)) {
                    title = name;
                } else if ("date".equals(type)) {
                    date = name;
                } else if ("status".equals(type)) {
                    status = name;
                }
            }
        }
        return new PropertyNames(title, date, status);
    }

    public List<String> resolvePropertyKeys(JsonNode databaseNode) {
        JsonNode props = databaseNode.path("properties");
        if (!props.isObject()) {
            return List.of();
        }
        ArrayList<String> keys = new ArrayList<>();
        props.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    public void requireProperty(String actualName, String requestedType, List<String> available) {
        if (actualName == null || actualName.isBlank()) {
            String message = "Missing property for type: " + requestedType
                    + ". Available properties: " + available;
            throw new CustomException(new DynamicErrorCode(message));
        }
    }
}
