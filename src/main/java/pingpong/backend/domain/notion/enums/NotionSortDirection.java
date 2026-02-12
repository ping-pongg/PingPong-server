package pingpong.backend.domain.notion.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notion sort direction")
public enum NotionSortDirection {
    ASCENDING("ascending"),
    DESCENDING("descending");

    private final String value;

    NotionSortDirection(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static NotionSortDirection fromValue(String value) {
        for (NotionSortDirection dir : values()) {
            if (dir.value.equalsIgnoreCase(value)) {
                return dir;
            }
        }
        throw new IllegalArgumentException("Invalid sort direction: " + value);
    }
}
