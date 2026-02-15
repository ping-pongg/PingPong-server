package pingpong.backend.global.auth.dto;

import pingpong.backend.domain.member.Member;

public record LoginResponse(
        Long userId,
        String nickname,
        String email
) {
    public static LoginResponse of(Member member) {
        return new LoginResponse(member.getId(), member.getNickname(), member.getEmail());
    }
}
