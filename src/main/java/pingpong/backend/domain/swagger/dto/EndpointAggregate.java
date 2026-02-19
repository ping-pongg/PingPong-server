package pingpong.backend.domain.swagger.dto;

import java.time.LocalDateTime;
import java.util.List;

import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.SwaggerParameter;
import pingpong.backend.domain.swagger.SwaggerRequest;
import pingpong.backend.domain.swagger.SwaggerResponse;

public record EndpointAggregate (
	Endpoint endpoint,
	List<SwaggerParameter> parameters,
	List<SwaggerRequest> requests,
	List<SwaggerResponse> responses,
	LocalDateTime createdAt
){
}
