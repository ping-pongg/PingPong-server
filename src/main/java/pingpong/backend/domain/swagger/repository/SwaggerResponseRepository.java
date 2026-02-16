package pingpong.backend.domain.swagger.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pingpong.backend.domain.swagger.SwaggerResponse;

public interface SwaggerResponseRepository extends JpaRepository<SwaggerResponse, Long> {
}
