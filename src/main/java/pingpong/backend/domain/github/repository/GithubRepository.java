package pingpong.backend.domain.github.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.github.Github;

public interface GithubRepository extends JpaRepository<Github, Long> {
	boolean existsByTeamId(Long teamId);
}
