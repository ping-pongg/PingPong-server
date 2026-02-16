package pingpong.backend.domain.swagger.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.swagger.SwaggerParameter;

public interface SwaggerParameterRepository extends JpaRepository<SwaggerParameter, Long> {
}
