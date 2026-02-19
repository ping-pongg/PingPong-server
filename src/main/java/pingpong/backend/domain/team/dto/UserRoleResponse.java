package pingpong.backend.domain.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.team.enums.Role;

@Schema(description = "팀 내 사용자 역할 응답")
public record UserRoleResponse(

        @Schema(description = "팀 내 역할", example = "BACKEND", implementation = Role.class)
        Role role
) {
    public static UserRoleResponse of(Role role) {
        return new UserRoleResponse(role);
    }
}
