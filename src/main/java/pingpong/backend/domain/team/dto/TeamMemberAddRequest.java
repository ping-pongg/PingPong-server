package pingpong.backend.domain.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import pingpong.backend.domain.team.enums.Role;

@Schema(description = "팀원 초대(추가) 요청")
public record TeamMemberAddRequest(

        @NotNull
        @Schema(description = "팀 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long teamId,

        @NotNull
        @Schema(description = "추가할 회원 ID", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        Long memberId,

        @NotNull
        @Schema(
                description = "팀 내 역할",
                example = "FRONTEND",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = Role.class
        )
        Role role
) {}