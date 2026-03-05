package pingpong.backend.domain.flow.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "request에 endpoint 연결")
public record FlowRequestConnectRequest(

	@Schema(description = "연결할 endpoint ID 목록", example = "[1, 2, 3]")
	@NotEmpty
	List<Long> endpointIds
) {}
