package pingpong.backend.domain.team.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.flow.Flow;
import pingpong.backend.domain.flow.FlowImage;
import pingpong.backend.domain.flow.repository.FlowImageRepository;
import pingpong.backend.domain.flow.repository.FlowRepository;
import pingpong.backend.domain.github.Github;
import pingpong.backend.domain.github.repository.GithubRepository;
import pingpong.backend.domain.github.service.GithubUrlParser;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.member.MemberErrorCode;
import pingpong.backend.domain.member.service.MemberService;
import pingpong.backend.domain.notion.repository.NotionRepository;
import pingpong.backend.domain.swagger.dto.response.EndpointGroupResponse;
import pingpong.backend.domain.swagger.event.SwaggerSyncInitEvent;
import pingpong.backend.domain.swagger.service.SsrfGuard;
import pingpong.backend.domain.team.MemberTeam;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.TeamErrorCode;
import pingpong.backend.domain.team.dto.*;
import pingpong.backend.domain.team.repository.MemberTeamRepository;
import pingpong.backend.domain.team.repository.TeamRepository;
import pingpong.backend.global.exception.CustomException;
import pingpong.backend.global.exception.ErrorCode;
import pingpong.backend.global.storage.service.PresignedUrlService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final MemberTeamRepository memberTeamRepository;
    private final MemberService memberService;
    private final NotionRepository notionRepository;
    private final SsrfGuard ssrfGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final FlowRepository flowRepository;
    private final FlowImageRepository flowImageRepository;
    private final PresignedUrlService presignedUrlService;
    private final GithubRepository githubRepository;

    /**
     * 팀 생성 + 생성자 자동 참여 (요구사항 1 반영)
     */
    @Transactional
    public TeamCreateResponse createTeam(TeamCreateRequest req, Member creator) {
        ssrfGuard.validate(req.swagger());
        Team team = Team.create(
                req.name(),
                req.figma(),
                req.discord(),
                req.swagger(),
                req.github()
        );

        Team savedTeam = teamRepository.save(team);

        memberTeamRepository.save(
                MemberTeam.of(savedTeam.getId(), creator.getId(), req.creatorRole())
        );

        GithubUrlParser.RepoInfo repoInfo= GithubUrlParser.parse(req.github());
        Github github=Github.create(repoInfo.owner(),repoInfo.repo(),req.githubBranch(),savedTeam);
        githubRepository.save(github);

        if (req.swagger() != null && !req.swagger().isBlank()) {
            eventPublisher.publishEvent(new SwaggerSyncInitEvent(savedTeam.getId(), creator));
        }

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

        List<Team> teams = teamRepository.findAllById(teamIds);

        Map<Long, Flow> firstFlowByTeamId = flowRepository.findFirstFlowPerTeam(teamIds)
                .stream()
                .collect(Collectors.toMap(f -> f.getTeam().getId(), Function.identity()));

        List<Long> flowIds = firstFlowByTeamId.values().stream()
                .map(Flow::getId)
                .toList();

        Map<Long, FlowImage> thumbnailByFlowId = flowIds.isEmpty()
                ? Map.of()
                : flowImageRepository.findFirstImagePerFlow(flowIds).stream()
                        .collect(Collectors.toMap(fi -> fi.getFlow().getId(), Function.identity()));

        return teams.stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(team -> {
                    Flow firstFlow = firstFlowByTeamId.get(team.getId());
                    String thumbnailUrl = null;
                    if (firstFlow != null) {
                        FlowImage thumbnail = thumbnailByFlowId.get(firstFlow.getId());
                        if (thumbnail != null) {
                            thumbnailUrl = presignedUrlService.getGetS3Url(thumbnail.getObjectKey()).presignedUrl();
                        }
                    }
                    return MyTeamResponse.of(team, thumbnailUrl);
                })
                .toList();
    }

    /**
     * 팀 정보 조회
     */
    @Transactional(readOnly = true)
    public TeamInfoResponse getTeamInfo(Long teamId, Member member) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException(TeamErrorCode.TEAM_NOT_FOUND));

        if (!memberTeamRepository.existsByTeamIdAndMemberId(teamId, member.getId())) {
            throw new CustomException(TeamErrorCode.TEAM_MEMBER_NOT_FOUND);
        }

        boolean notionConnected = notionRepository.findByTeamId(teamId).isPresent();
        return TeamInfoResponse.of(team, notionConnected);
    }

    /**
     * 특정 팀에서 현재 사용자의 역할 조회
     */
    @Transactional(readOnly = true)
    public UserRoleResponse getUserRole(Long teamId, Member member) {
        teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException(TeamErrorCode.TEAM_NOT_FOUND));

        MemberTeam memberTeam = memberTeamRepository.findByTeamIdAndMemberId(teamId, member.getId())
                .orElseThrow(() -> new CustomException(TeamErrorCode.TEAM_MEMBER_NOT_FOUND));

        return UserRoleResponse.of(memberTeam.getRole());
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

