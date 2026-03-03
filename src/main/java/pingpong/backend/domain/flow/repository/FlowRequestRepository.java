package pingpong.backend.domain.flow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pingpong.backend.domain.flow.FlowRequest;

public interface FlowRequestRepository extends JpaRepository<FlowRequest, Long> {

	@Query("""
		select r from FlowRequest r
		where r.image.id = :imageId
	""")
	List<FlowRequest> findByImageId(@Param("imageId") Long imageId);
}
