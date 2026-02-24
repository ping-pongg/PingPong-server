package pingpong.backend.domain.flow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.flow.FlowImage;

public interface FlowImageRepository extends JpaRepository<FlowImage, Long> {
}
