package pingpong.backend.domain.team.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.member.MemberErrorCode;
import pingpong.backend.domain.member.service.MemberService;
import pingpong.backend.domain.team.MemberTeam;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.TeamErrorCode;
import pingpong.backend.domain.team.dto.*;
import pingpong.backend.domain.team.repository.MemberTeamRepository;
import pingpong.backend.domain.team.repository.TeamRepository;
import pingpong.backend.global.exception.CustomException;
import pingpong.backend.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final MemberTeamRepository memberTeamRepository;
    private final MemberService memberService;

    /**
     * 팀 생성 + 생성자 자동 참여 (요구사항 1 반영)
     */
    @Transactional
    public TeamCreateResponse createTeam(TeamCreateRequest req, Member creator) {
        Team team = Team.create(
                req.name(),
                req.notion(),
                req.figma(),
                req.discord(),
                req.swagger(),
                req.github()
        );

        Team savedTeam = teamRepository.save(team);

        memberTeamRepository.save(
                MemberTeam.of(savedTeam.getId(), creator.getId(), req.creatorRole())
        );

        return new TeamCreateResponse(savedTeam.getId());
    }

    /**
     * 팀에 팀원들 추가 (List 요청)
     */
    @Transactional
    public void addMembersToTeam(List<TeamMemberAddRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        TeamMemberAddRequest first = reqs.getFirst();
        if (first == null || first.teamId() == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        Long teamId = first.teamId();
        boolean sameTeam = reqs.stream()
                .allMatch(r -> r != null && teamId.equals(r.teamId()));
        if (!sameTeam) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException(TeamErrorCode.TEAM_NOT_FOUND));

        for (TeamMemberAddRequest req : reqs) {
            if (req.memberId() == null || req.role() == null) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }

            Member member = memberService.findById(req.memberId());

            if (memberTeamRepository.existsByTeamIdAndMemberId(team.getId(), member.getId())) {
                throw new CustomException(TeamErrorCode.TEAM_MEMBER_ALREADY_EXISTS);
            }

            memberTeamRepository.save(MemberTeam.of(team.getId(), member.getId(), req.role()));
        }
    }

    /**
     * 내가 참여 중인 팀 조회
     */
    @Transactional(readOnly = true)
    public List<MyTeamResponse> getMyTeams(Member me) {
        List<MemberTeam> links = memberTeamRepository.findAllByMemberId(me.getId());
        if (links.isEmpty()) return List.of();

        List<Long> teamIds = links.stream()
                .map(MemberTeam::getTeamId)
                .distinct()
                .toList();

        return teamRepository.findAllById(teamIds).stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(MyTeamResponse::of)
                .toList();
    }

    /**
     * 팀의 팀원들 목록 조회
     */
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getTeamMembers(Long teamId) {
        teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException(TeamErrorCode.TEAM_NOT_FOUND));

        List<MemberTeam> memberTeams = memberTeamRepository.findAllByTeamId(teamId);
        if (memberTeams.isEmpty()) return List.of();

        List<Long> memberIds = memberTeams.stream()
                .map(MemberTeam::getMemberId)
                .distinct()
                .toList();

        Map<Long, Member> memberMap = memberService.findAllByIds(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, m -> m));

        return memberTeams.stream()
                .map(mt -> {
                    Member m = memberMap.get(mt.getMemberId());
                    if (m == null) {
                        throw new CustomException(MemberErrorCode.MEMBER_NOT_FOUND);
                    }
                    return TeamMemberResponse.of(m, mt.getRole());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Team getTeam(Long teamId) {
        return teamRepository.findById(teamId)
            .orElseThrow(() -> new CustomException(TeamErrorCode.TEAM_NOT_FOUND));
    }
}

