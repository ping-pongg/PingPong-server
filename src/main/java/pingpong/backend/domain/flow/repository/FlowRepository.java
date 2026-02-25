package pingpong.backend.domain.flow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pingpong.backend.domain.flow.Flow;

public interface FlowRepository extends JpaRepository<Flow, Long> {

}
