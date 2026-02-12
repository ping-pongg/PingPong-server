package pingpong.backend.domain.notion.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Logical operator for timestamp filters")
public enum NotionQueryLogic {
    AND,
    OR
}
