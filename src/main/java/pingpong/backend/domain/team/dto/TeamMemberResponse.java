package pingpong.backend.domain.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.team.enums.Role;

@Schema(description = "팀원 목록/상세 응답")
public record TeamMemberResponse(

        @Schema(description = "회원 ID", example = "1")
        Long memberId,

        @Schema(description = "이름(닉네임)", example = "minji")
        String name,

        @Schema(description = "이메일", example = "test@gmail.com")
        String email,

        @Schema(description = "팀 내 역할", example = "BACKEND", implementation = Role.class)
        Role role
) {
    public static TeamMemberResponse of(Member member, Role role) {
        return new TeamMemberResponse(
                member.getId(),
                member.getNickname(),
                member.getEmail(),
                role
        );
    }
}