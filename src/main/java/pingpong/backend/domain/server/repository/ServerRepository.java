package pingpong.backend.domain.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pingpong.backend.domain.server.Server;

public interface ServerRepository extends JpaRepository<Server, Long> {

	@Query("""
	SELECT COUNT(s)>0 
	FROM Server s
	JOIN Team t ON t.id=s.team.id
	WHERE s.id=:serverId
	AND t.id=:teamId
	""")
	boolean canAccessServer(Long serverId, Long memberId);
}
