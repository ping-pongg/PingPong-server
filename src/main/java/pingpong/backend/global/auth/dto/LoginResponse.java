package pingpong.backend.global.auth.dto;

import pingpong.backend.domain.member.Member;

public record LoginResponse(
        Long userId,
        String nickname
) {
    public static LoginResponse of(Member member) {
        return new LoginResponse(member.getId(), member.getNickname());
    }
}
