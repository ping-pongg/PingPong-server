package pingpong.backend.domain.team.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pingpong.backend.domain.team.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
