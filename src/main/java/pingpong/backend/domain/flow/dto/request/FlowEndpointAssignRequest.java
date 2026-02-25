package pingpong.backend.domain.flow.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "플로우 이미지에 api 할당")
public record FlowEndpointAssignRequest (

	@Schema(description="endpoint ID",example="1")
	Long endpointId,

	@Schema(description="이미지 내 엔드포인트 위치의 X 좌표",example="1")
	Float x,

	@Schema(description="이미지 내 엔드포인트 위치의 Y 좌표",example="1")
	Float y
	){
}
