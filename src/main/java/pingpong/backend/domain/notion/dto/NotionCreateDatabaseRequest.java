package pingpong.backend.domain.notion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(description = "Notion 데이터베이스 생성 요청 (알 수 없는 필드는 거부됨)")
public record NotionCreateDatabaseRequest(
        @NotBlank
        @Schema(description = "데이터베이스 제목",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "프로젝트 DB")
        String title,

        @Schema(description = "데이터베이스 설명",
                example = "프로젝트 일정 관리용")
        String description,

        @Schema(description = "인라인 DB 여부 (is_inline)",
                example = "true")
        Boolean isInline,

        @NotNull
        @Schema(description = "DB 속성 스키마",
                example = """
                        {
                          "이름": { "type": "title", "title": {} },
                          "기한": { "type": "date", "date": {} }
                        }
                        """)
        Map<String, JsonNode> properties,

        @Schema(description = "아이콘 오브젝트 (선택, Notion 포맷)",
                example = """
                        {
                          "type": "emoji",
                          "emoji": "🚀"
                        }
                        """)
        JsonNode icon,

        @Schema(description = "커버 오브젝트 (선택, Notion 포맷)",
                example = """
                        {
                          "type": "external",
                          "external": {
                            "url": "https://example.com/cover.png"
                          }
                        }
                        """)
        JsonNode cover
) {
}
