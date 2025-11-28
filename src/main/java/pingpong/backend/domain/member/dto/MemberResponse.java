package pingpong.backend.domain.member.dto;

import pingpong.backend.domain.member.Member;

public record MemberResponse(
        Long userId,
        String email,
        String nickname
) {
    public static MemberResponse of(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getNickname());
    }
}

