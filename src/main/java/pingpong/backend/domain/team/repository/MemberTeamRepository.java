package pingpong.backend.domain.team.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pingpong.backend.domain.team.MemberTeam;

import java.util.List;

public interface MemberTeamRepository extends JpaRepository<MemberTeam, Long> {

    boolean existsByTeamIdAndMemberId(Long teamId, Long memberId);
    List<MemberTeam> findAllByMemberId(Long memberId);
    List<MemberTeam> findAllByTeamId(Long teamId);
}

