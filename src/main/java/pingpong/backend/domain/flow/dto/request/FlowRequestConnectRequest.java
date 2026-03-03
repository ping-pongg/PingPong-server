package pingpong.backend.domain.flow.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "request에 endpoint 연결")
public record FlowRequestConnectRequest(

	@Schema(description = "연결할 endpoint ID", example = "1")
	Long endpointId
) {}
