package pingpong.backend.domain.flow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pingpong.backend.domain.flow.FlowImageEndpoint;
import pingpong.backend.domain.swagger.Endpoint;

public interface FlowImageEndpointRepository extends JpaRepository<FlowImageEndpoint, Long> {

	@Query("""
        select fie
        from FlowImageEndpoint fie
        join fetch fie.endpoint e
        where fie.image.id = :flowImageId
    """)
	List<FlowImageEndpoint> findMappingsByImageId(@Param("flowImageId") Long flowImageId);
	Optional<FlowImageEndpoint> findByImageIdAndEndpointId(Long flowImageId, Long endpointId);
	List<FlowImageEndpoint> findAllByImageId(Long imageId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
    update FlowImageEndpoint fie
    set fie.isLinked = false
    where fie.endpoint.id in :endpointIds
      and fie.isLinked = true
""")
	int unlinkChangedEndpoints(@Param("endpointIds") List<Long> endpointIds);

}

