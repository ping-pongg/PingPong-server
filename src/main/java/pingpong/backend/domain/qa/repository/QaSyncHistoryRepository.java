package pingpong.backend.domain.qa.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.qa.QaSyncHistory;

public interface QaSyncHistoryRepository extends JpaRepository<QaSyncHistory, Long> {
	/**
	 * 특정 팀의 가장 최근 동기화 이력 1건 조회
	 * OrderByIdDesc: ID 내림차순 (최신순)
	 * findTop: 그 중 첫 번째 레코드만 반환
	 */
	Optional<QaSyncHistory> findTopByTeamIdOrderByIdDesc(Long teamId);
}
