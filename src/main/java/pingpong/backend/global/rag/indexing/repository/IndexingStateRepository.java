package pingpong.backend.global.rag.indexing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pingpong.backend.global.rag.indexing.IndexingState;

import java.util.Optional;

public interface IndexingStateRepository extends JpaRepository<IndexingState, Long> {

    Optional<IndexingState> findBySourceKey(String sourceKey);
}
