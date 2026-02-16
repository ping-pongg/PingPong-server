package pingpong.backend.domain.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pingpong.backend.domain.server.Server;

public interface ServerRepository extends JpaRepository<Server, Long> {

}
