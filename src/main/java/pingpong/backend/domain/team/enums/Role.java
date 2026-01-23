package pingpong.backend.domain.team.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "팀 내 역할")
public enum Role {
    @Schema(description = "프론트엔드") FRONTEND,
    @Schema(description = "백엔드") BACKEND,
    @Schema(description = "기획") PLANNING,
    @Schema(description = "QA") QA
}