package pingpong.backend.domain.flow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pingpong.backend.domain.flow.RequestEndpoint;
import pingpong.backend.domain.swagger.Endpoint;

public interface RequestEndpointRepository extends JpaRepository<RequestEndpoint, Long> {

	@Query("""
		select re from RequestEndpoint re
		join fetch re.endpoint
		where re.request.id = :requestId
	""")
	List<RequestEndpoint> findByRequestIdWithEndpoint(@Param("requestId") Long requestId);

	@Query("""
		select re from RequestEndpoint re
		join fetch re.request r
		join fetch re.endpoint
		where r.image.id = :imageId
	""")
	List<RequestEndpoint> findByImageIdWithAll(@Param("imageId") Long imageId);

	boolean existsByRequestIdAndEndpointId(Long requestId, Long endpointId);

	@Query("""
		select re from RequestEndpoint re
		join fetch re.endpoint
		where re.endpoint.id = :endpointId
	""")
	List<RequestEndpoint> findAllByEndpointId(@Param("endpointId") Long endpointId);

	@Query("""
		select re from RequestEndpoint re
		join fetch re.request r
		where r.image.id = :imageId
		  and re.endpoint.id = :endpointId
	""")
	List<RequestEndpoint> findByImageIdAndEndpointId(
		@Param("imageId") Long imageId,
		@Param("endpointId") Long endpointId
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update RequestEndpoint re
		set re.isLinked = false
		where re.endpoint.id in :endpointIds
		  and re.isLinked = true
	""")
	int unlinkChangedEndpoints(@Param("endpointIds") List<Long> endpointIds);

	@Query("""
		select distinct fi.flow.id
		from RequestEndpoint re
		join re.request r
		join r.image fi
		where fi.flow.id in :flowIds
	""")
	List<Long> findFlowIdsWithAnyEndpoint(@Param("flowIds") List<Long> flowIds);

	@Query("""
		select distinct fi.flow.id
		from RequestEndpoint re
		join re.request r
		join r.image fi
		where fi.flow.id in :flowIds
		  and re.isLinked = false
	""")
	List<Long> findFlowIdsWithUnlinkedEndpoint(@Param("flowIds") List<Long> flowIds);

	@Query("""
		SELECT DISTINCT re.endpoint
		FROM RequestEndpoint re
		JOIN re.request r
		JOIN r.image fi
		WHERE fi.flow.id IN :flowIds
	""")
	List<Endpoint> findDistinctEndpointsByFlowIds(@Param("flowIds") List<Long> flowIds);
}
