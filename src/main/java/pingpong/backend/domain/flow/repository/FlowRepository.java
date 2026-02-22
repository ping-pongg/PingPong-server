package pingpong.backend.domain.flow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.flow.Flow;

public interface FlowRepository extends JpaRepository<Flow, Long> {

}
