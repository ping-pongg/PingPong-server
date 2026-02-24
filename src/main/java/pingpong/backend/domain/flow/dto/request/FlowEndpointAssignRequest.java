package pingpong.backend.domain.flow.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "플로우에 api 할당")
public record FlowEndpointAssignRequest (

	@Schema(description="endpoint ID",example="1")
	Long endpointId

){
}
