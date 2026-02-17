package pingpong.backend.global.rag.indexing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pingpong.backend.global.rag.indexing.IndexingState;
import pingpong.backend.global.rag.indexing.enums.IndexSourceType;

import java.util.Optional;

public interface IndexingStateRepository extends JpaRepository<IndexingState, Long> {

    Optional<IndexingState> findBySourceKey(String sourceKey);

    Optional<IndexingState> findBySourceTypeAndTeamIdAndResourceId(IndexSourceType sourceType, Long teamId, String resourceId);
}
