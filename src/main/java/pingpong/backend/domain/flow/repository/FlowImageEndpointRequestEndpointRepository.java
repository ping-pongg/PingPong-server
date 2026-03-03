package pingpong.backend.domain.flow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pingpong.backend.domain.flow.FlowImageEndpointRequestEndpoint;

public interface FlowImageEndpointRequestEndpointRepository
	extends JpaRepository<FlowImageEndpointRequestEndpoint, Long> {

	@Query("""
		select l from FlowImageEndpointRequestEndpoint l
		join fetch l.endpoint
		where l.request.id = :requestId
	""")
	List<FlowImageEndpointRequestEndpoint> findByRequestIdWithEndpoint(@Param("requestId") Long requestId);

	@Query("""
		select l from FlowImageEndpointRequestEndpoint l
		join fetch l.request r
		join fetch l.endpoint
		where r.image.id = :imageId
	""")
	List<FlowImageEndpointRequestEndpoint> findByImageIdWithAll(@Param("imageId") Long imageId);

	boolean existsByRequestIdAndEndpointId(Long requestId, Long endpointId);

	@Query("""
		select l from FlowImageEndpointRequestEndpoint l
		join fetch l.endpoint
		where l.endpoint.id = :endpointId
	""")
	List<FlowImageEndpointRequestEndpoint> findAllByEndpointId(@Param("endpointId") Long endpointId);

	@Query("""
		select l from FlowImageEndpointRequestEndpoint l
		join fetch l.request r
		where r.image.id = :imageId
		  and l.endpoint.id = :endpointId
	""")
	List<FlowImageEndpointRequestEndpoint> findByImageIdAndEndpointId(
		@Param("imageId") Long imageId,
		@Param("endpointId") Long endpointId
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update FlowImageEndpointRequestEndpoint l
		set l.isLinked = false
		where l.endpoint.id in :endpointIds
		  and l.isLinked = true
	""")
	int unlinkChangedEndpoints(@Param("endpointIds") List<Long> endpointIds);

	@Query("""
		select distinct fi.flow.id
		from FlowImageEndpointRequestEndpoint l
		join l.request r
		join r.image fi
		where fi.flow.id in :flowIds
	""")
	List<Long> findFlowIdsWithAnyEndpoint(@Param("flowIds") List<Long> flowIds);

	@Query("""
		select distinct fi.flow.id
		from FlowImageEndpointRequestEndpoint l
		join l.request r
		join r.image fi
		where fi.flow.id in :flowIds
		  and l.isLinked = false
	""")
	List<Long> findFlowIdsWithUnlinkedEndpoint(@Param("flowIds") List<Long> flowIds);
}
