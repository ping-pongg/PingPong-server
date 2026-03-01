package pingpong.backend.domain.swagger.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.swagger.SwaggerEndpointSecurity;

public interface SwaggerEndpointSecurityRepository
	extends JpaRepository<SwaggerEndpointSecurity,Long> {
	List<SwaggerEndpointSecurity> findByEndpointId(Long endpointId);
}
