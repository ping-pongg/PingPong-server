package pingpong.backend.domain.member.dto;

import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.member.MemberMcpConnection;

import java.time.LocalDateTime;

public record MemberResponse(
        Long userId,
        String email,
        String nickname,
        Boolean mcpConnected,
        Long mcpTeamId,
        String mcpTeamName,
        LocalDateTime mcpLastUsedAt
) {
    public static MemberResponse of(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getNickname(),
                false, null, null, null);
    }

    public static MemberResponse of(Member member, MemberMcpConnection conn, String teamName) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                conn != null,
                conn != null ? conn.getTeamId() : null,
                conn != null ? teamName : null,
                conn != null ? conn.getLastUsedAt() : null
        );
    }
}
