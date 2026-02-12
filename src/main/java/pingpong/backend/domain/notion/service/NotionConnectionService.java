package pingpong.backend.domain.notion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.notion.Notion;
import pingpong.backend.domain.notion.NotionErrorCode;
import pingpong.backend.domain.notion.repository.NotionRepository;
import pingpong.backend.domain.team.TeamErrorCode;
import pingpong.backend.domain.team.repository.MemberTeamRepository;
import pingpong.backend.domain.team.repository.TeamRepository;
import pingpong.backend.global.exception.CustomException;

@Service
@RequiredArgsConstructor
public class NotionConnectionService {

    private final MemberTeamRepository memberTeamRepository;
    private final NotionRepository notionRepository;
    private final TeamRepository teamRepository;

    public void assertTeamAccess(Long teamId, Member member) {
        if (!teamRepository.existsById(teamId)) {
            throw new CustomException(TeamErrorCode.TEAM_NOT_FOUND);
        }
        if (!memberTeamRepository.existsByTeamIdAndMemberId(teamId, member.getId())) {
            throw new CustomException(NotionErrorCode.NOTION_PERMISSION_DENIED);
        }
    }

    @Transactional(readOnly = true)
    public String resolveConnectedDatabaseId(Long teamId) {
        Notion notion = notionRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(NotionErrorCode.NOTION_NOT_CONNECTED));
        String databaseId = notion.getDatabaseId();
        if (databaseId == null || databaseId.isBlank()) {
            throw new CustomException(NotionErrorCode.NOTION_DATABASE_NOT_SET);
        }
        return databaseId;
    }
}
