package pingpong.backend.domain.swagger.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.swagger.SwaggerParameter;

public interface SwaggerParameterRepository extends JpaRepository<SwaggerParameter, Long> {

	List<SwaggerParameter> findByEndpointId(Long endpointId);

	List<SwaggerParameter> findByEndpointIdInAndInType(Collection<Long> endpointIds, String inType);
}
