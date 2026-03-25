package pingpong.backend.domain.qa.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.qa.QaParamDefault;

public interface QaParamDefaultRepository extends JpaRepository<QaParamDefault, Long> {

	List<QaParamDefault> findByTeamId(Long teamId);

	void deleteByTeamId(Long teamId);
}
