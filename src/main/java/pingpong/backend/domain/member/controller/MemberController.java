package pingpong.backend.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.member.dto.MemberRegisterRequest;
import pingpong.backend.domain.member.dto.MemberResponse;
import pingpong.backend.domain.member.dto.MemberSearchResponse;
import pingpong.backend.domain.member.service.MemberService;
import pingpong.backend.domain.team.service.TeamService;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "회원 API", description = "회원 관련 API 입니다.")
@RequestMapping("api/v1/members")
public class MemberController {

    private final MemberService memberService;
    private final TeamService teamService;

    @PostMapping
    @Operation(summary = "회원가입", description = "이메일과 비밀번호로 회원가입 합니다.")
    public SuccessResponse<MemberResponse> registerMember(
            @RequestBody @Valid MemberRegisterRequest registerRequest
    ) {
        MemberResponse response = memberService.register(registerRequest);
        return SuccessResponse.ok(response);
    }

    @GetMapping("/{memberId}")
    @Operation(summary = "회원 단건 조회", description = "memberId로 회원 정보를 조회합니다.")
    public SuccessResponse<MemberResponse> findMember(
            @PathVariable Long memberId
    ) {
        MemberResponse response = memberService.find(memberId);
        return SuccessResponse.ok(response);
    }

    @GetMapping
    @Operation(summary = "내 정보 조회", description = "내 정보를 조회합니다.")
    public SuccessResponse<MemberResponse> findMe(
            @CurrentMember Member member
    ) {
        return SuccessResponse.ok(MemberResponse.of(member));
    }

    @GetMapping("/search")
    @Operation(
            summary = "이메일로 회원 검색",
            description = "이메일의 일부만 입력해도 검색 결과를 반환합니다."
    )
    public SuccessResponse<List<MemberSearchResponse>> searchByEmail(
            @RequestParam("keyword") String keyword
    ) {
        return SuccessResponse.ok(teamService.searchMembersByEmail(keyword));
    }
}
