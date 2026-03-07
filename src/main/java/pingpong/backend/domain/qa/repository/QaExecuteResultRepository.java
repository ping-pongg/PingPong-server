package pingpong.backend.domain.qa.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.qa.QaExecuteResult;

public interface QaExecuteResultRepository extends JpaRepository<QaExecuteResult, Long> {

	List<QaExecuteResult> findAllByQaCaseIdOrderByExecutedAtDesc(Long qaCaseId);

	java.util.Optional<QaExecuteResult> findTopByQaCaseIdOrderByExecutedAtDesc(Long qaCaseId);
}
