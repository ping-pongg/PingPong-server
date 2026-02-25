package pingpong.backend.domain.swagger.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "태그별로 endpoint 반환")
public record EndpointGroupResponse (

	@Schema
	String tag,

	@Schema
	List<EndpointResponse> endpoints
){
}
