package pingpong.backend.domain.flow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pingpong.backend.domain.flow.FlowImageEndpointRequest;

public interface FlowImageEndpointRequestRepository extends JpaRepository<FlowImageEndpointRequest, Long> {

	@Query("""
		select r from FlowImageEndpointRequest r
		where r.image.id = :imageId
	""")
	List<FlowImageEndpointRequest> findByImageId(@Param("imageId") Long imageId);
}
