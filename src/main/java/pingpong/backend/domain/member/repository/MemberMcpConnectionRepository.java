package pingpong.backend.domain.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pingpong.backend.domain.member.MemberMcpConnection;

import java.util.Optional;

public interface MemberMcpConnectionRepository extends JpaRepository<MemberMcpConnection, Long> {

    Optional<MemberMcpConnection> findByMemberId(Long memberId);
}
