package pingpong.backend.domain.github.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.github.Github;

public interface GithubRepository extends JpaRepository<Github, Long> {
	boolean existsByTeamId(Long teamId);
	Optional<Github> findByTeamId(Long teamId);
}
