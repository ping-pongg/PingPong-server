package pingpong.backend.domain.qa.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import pingpong.backend.domain.qa.QaCase;

public interface QaCaseRepository extends JpaRepository<QaCase, Long> {

	List<QaCase> findAllByEndpointId(Long endpointId);

	List<QaCase> findAllByEndpointIdIn(Collection<Long> endpointIds);
}
