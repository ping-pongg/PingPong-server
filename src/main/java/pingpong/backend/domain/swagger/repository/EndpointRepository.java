package pingpong.backend.domain.swagger.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.swagger.Endpoint;

public interface EndpointRepository extends JpaRepository<Endpoint, Long> {
	List<Endpoint> findBySnapshotId(Long snapshotId);
}
