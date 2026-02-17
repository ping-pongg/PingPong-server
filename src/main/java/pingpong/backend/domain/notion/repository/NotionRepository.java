package pingpong.backend.domain.notion.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pingpong.backend.domain.notion.Notion;

import java.util.Optional;

public interface NotionRepository extends JpaRepository<Notion, Long> {

    @Query("select n from Notion n where n.team.id = :teamId")
    Optional<Notion> findByTeamId(@Param("teamId") Long teamId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notion n where n.team.id = :teamId")
    Optional<Notion> findByTeamIdForUpdate(@Param("teamId") Long teamId);

    @Query("select n from Notion n where n.workspaceId = :workspaceId")
    Optional<Notion> findByWorkspaceId(@Param("workspaceId") String workspaceId);

}
