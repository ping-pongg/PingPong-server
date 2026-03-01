package pingpong.backend.domain.flow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pingpong.backend.domain.flow.FlowImage;

public interface FlowImageRepository extends JpaRepository<FlowImage, Long> {
	List<FlowImage> findByFlowId(Long flowId);
	Optional<FlowImage> findById(Long id);

	@Query("""
		select fi
		from FlowImage fi
		where fi.flow.id in :flowIds
		  and fi.id = (
		    select min(fi2.id)
		    from FlowImage fi2
		    where fi2.flow.id = fi.flow.id
		  )
		""")
	List<FlowImage> findFirstImagePerFlow(@Param("flowIds") List<Long> flowIds);
}
