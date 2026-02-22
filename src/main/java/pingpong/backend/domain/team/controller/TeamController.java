package pingpong.backend.domain.team.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.team.dto.*;
import pingpong.backend.domain.team.service.TeamService;
import pingpong.backend.global.annotation.CurrentMember;
import pingpong.backend.global.response.result.SuccessResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teams")
@Tag(name = "팀 API", description = "팀 관련 API 입니다.")
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    @Operation(summary = "팀 생성", description = "팀을 생성하고 생성자를 팀원으로 자동 등록합니다.")
    public SuccessResponse<TeamCreateResponse> createTeam(
            @CurrentMember Member member,
            @RequestBody @Valid TeamCreateRequest req
    ) {
        return SuccessResponse.ok(teamService.createTeam(req, member));
    }

    @PostMapping("/members")
    @Operation(summary = "팀원 추가", description = "teamId와 memberId로 팀원을 추가합니다. (List 요청 지원)")
    public SuccessResponse<Void> addMembers(
            @RequestBody @Valid List<@Valid TeamMemberAddRequest> reqs
    ) {
        teamService.addMembersToTeam(reqs);
        return SuccessResponse.ok(null);
    }

    @GetMapping("/my")
    @Operation(summary = "내가 참여 중인 팀 조회", description = "현재 로그인한 사용자가 참여 중인 팀 목록을 조회합니다.")
    public SuccessResponse<List<MyTeamResponse>> myTeams(
            @CurrentMember Member member
    ) {
        return SuccessResponse.ok(teamService.getMyTeams(member));
    }

    @GetMapping("/{teamId}")
    @Operation(summary = "팀 정보 조회", description = "teamId로 팀 정보를 조회합니다.")
    public SuccessResponse<TeamInfoResponse> getTeamInfo(
            @PathVariable Long teamId,
            @CurrentMember Member member
    ) {
        return SuccessResponse.ok(teamService.getTeamInfo(teamId, member));
    }

    @GetMapping("/{teamId}/members")
    @Operation(
            summary = "팀원 목록 조회",
            description = "teamId를 입력받아 해당 팀에 속한 회원 목록을 조회합니다."
    )
    public SuccessResponse<List<TeamMemberResponse>> getTeamMembers(
            @PathVariable Long teamId
    ) {
        return SuccessResponse.ok(
                teamService.getTeamMembers(teamId)
        );
    }

    @GetMapping("/{teamId}/my-role")
    @Operation(
            summary = "팀 내 내 역할 조회",
            description = "JWT 토큰과 팀 ID를 기반으로 해당 팀에서 현재 로그인한 사용자의 역할을 조회합니다."
    )
    public SuccessResponse<UserRoleResponse> getMyRole(
            @PathVariable Long teamId,
            @CurrentMember Member member
    ) {
        return SuccessResponse.ok(teamService.getUserRole(teamId, member));
    }

}

