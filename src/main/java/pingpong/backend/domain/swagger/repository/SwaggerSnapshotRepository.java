package pingpong.backend.domain.swagger.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pingpong.backend.domain.swagger.SwaggerSnapshot;

public interface SwaggerSnapshotRepository extends JpaRepository<SwaggerSnapshot, Long> {

	Optional<SwaggerSnapshot> findTopByServerIdOrderByIdDesc(Long serverId);

}
