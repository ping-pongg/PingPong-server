package pingpong.backend.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.member.MemberMcpConnection;
import pingpong.backend.domain.member.repository.MemberMcpConnectionRepository;
import pingpong.backend.domain.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor
public class MemberMcpConnectionService {

    private final MemberMcpConnectionRepository mcpConnectionRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void upsertConnection(Long memberId, Long teamId) {
        mcpConnectionRepository.findByMemberId(memberId)
                .ifPresentOrElse(
                        conn -> {
                            conn.updateTeamId(teamId);
                            conn.updateLastUsedAt();
                        },
                        () -> mcpConnectionRepository.save(MemberMcpConnection.create(memberId, teamId))
                );
    }

    @Async
    @Transactional
    public void updateLastUsedAt(String email) {
        memberRepository.findByEmail(email)
                .flatMap(member -> mcpConnectionRepository.findByMemberId(member.getId()))
                .ifPresent(MemberMcpConnection::updateLastUsedAt);
    }
}
