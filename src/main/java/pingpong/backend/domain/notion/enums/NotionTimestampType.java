package pingpong.backend.domain.notion.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notion timestamp type")
public enum NotionTimestampType {
    CREATED_TIME("created_time"),
    LAST_EDITED_TIME("last_edited_time");

    private final String value;

    NotionTimestampType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static NotionTimestampType fromValue(String value) {
        for (NotionTimestampType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid timestamp type: " + value);
    }
}
