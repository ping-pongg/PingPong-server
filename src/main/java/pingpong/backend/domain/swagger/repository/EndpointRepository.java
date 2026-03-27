package pingpong.backend.domain.swagger.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerErrorCode;
import pingpong.backend.domain.swagger.enums.CrudMethod;
import pingpong.backend.global.exception.CustomException;

public interface EndpointRepository extends JpaRepository<Endpoint, Long> {
	List<Endpoint> findBySnapshotId(Long snapshotId);
 	Endpoint findTopByPathAndMethodAndSnapshotCreatedAtLessThanOrderBySnapshotCreatedAtDesc(
		 String path, CrudMethod method, LocalDateTime createdAt
	);
	List<Endpoint> findAllByIdIn(Collection<Long> ids);

	List<Endpoint> findBySnapshotIdAndPathContainingIgnoreCase(Long snapshotId, String query);

	@Query("SELECT e FROM Endpoint e WHERE e.snapshot.team.id = :teamId")
	List<Endpoint> findAllBySnapshotTeamId(@Param("teamId") Long teamId);

	@Query("SELECT e FROM Endpoint e WHERE e.snapshot.team.id IN :teamIds")
	List<Endpoint> findAllBySnapshotTeamIdIn(@Param("teamIds") Collection<Long> teamIds);
}
