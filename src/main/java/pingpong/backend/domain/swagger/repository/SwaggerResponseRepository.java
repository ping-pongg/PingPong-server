package pingpong.backend.domain.swagger.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.swagger.SwaggerResponse;

public interface SwaggerResponseRepository extends JpaRepository<SwaggerResponse, Long> {

	List<SwaggerResponse> findByEndpointId(Long endpointId);
}
