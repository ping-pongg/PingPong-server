package pingpong.backend.domain.flow.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "flow에 endpoint를 할당한 결과입니다")
public record FlowEndpointAssignResponse(
	Long flowImageId,
	List<ImageEndpointsResponse> endpoints
) {}
