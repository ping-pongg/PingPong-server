package pingpong.backend.domain.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "팀 생성 응답")
public record TeamCreateResponse(

        @Schema(description = "생성된 팀 ID", example = "1")
        Long teamId
) {}