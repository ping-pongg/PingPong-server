package pingpong.backend.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.member.Member;

@Schema(description = "이메일 검색 결과 회원 정보")
public record MemberSearchResponse(

        @Schema(description = "회원 ID", example = "1")
        Long memberId,

        @Schema(description = "회원 이메일", example = "example@gmail.com")
        String email,

        @Schema(description = "회원 닉네임", example = "minji")
        String nickname
) {
    public static MemberSearchResponse of(Member member) {
        return new MemberSearchResponse(member.getId(), member.getEmail(), member.getNickname());
    }
}