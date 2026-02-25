package pingpong.backend.domain.flow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.flow.FlowImage;

public interface FlowImageRepository extends JpaRepository<FlowImage, Long> {
	List<FlowImage> findByFlowId(Long flowId);
	Optional<FlowImage> findById(Long id);
}
