package pingpong.backend.domain.swagger.repository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.enums.CrudMethod;
import pingpong.backend.global.exception.CustomException;

public interface EndpointRepository extends JpaRepository<Endpoint, Long> {
	List<Endpoint> findBySnapshotId(Long snapshotId);
 	Endpoint findTopByPathAndMethodAndSnapshotCreatedAtLessThanOrderBySnapshotCreatedAtDesc(
		 String path, CrudMethod method, LocalDateTime createdAt
	);



}
