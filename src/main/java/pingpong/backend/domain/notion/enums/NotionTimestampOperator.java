package pingpong.backend.domain.notion.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notion timestamp operator")
public enum NotionTimestampOperator {
    BEFORE("before"),
    AFTER("after"),
    ON_OR_BEFORE("on_or_before"),
    ON_OR_AFTER("on_or_after"),
    EQUALS("equals");

    private final String value;

    NotionTimestampOperator(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static NotionTimestampOperator fromValue(String value) {
        for (NotionTimestampOperator op : values()) {
            if (op.value.equalsIgnoreCase(value)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Invalid timestamp operator: " + value);
    }
}
