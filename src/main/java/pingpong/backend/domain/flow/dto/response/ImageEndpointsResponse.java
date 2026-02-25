package pingpong.backend.domain.flow.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.flow.FlowImageEndpoint;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.enums.CrudMethod;

@Schema(description = "endpoint의 이미지 내에 할당된 엔드포인트 목록")
public record ImageEndpointsResponse (

	@Schema(description = "endpoint ID")
	Long endpointId,

	@Schema(description = "태그명")
	String tag,

	@Schema(description = "엔드포인트 path")
	String path,

	@Schema(description = "method")
	CrudMethod method,

	@Schema(description = "엔드포인트 요약")
	String summary,

	@Schema(description="변화 여부")
	Boolean isChanged,

	@Schema(description="연동 여부")
	Boolean isLinked,

	@Schema(description = "이미지 내 X 좌표")
	Float x,

	@Schema(description = "이미지 내 Y 좌표")
	Float y

){
	public static ImageEndpointsResponse from(FlowImageEndpoint mapping) {
		Endpoint e = mapping.getEndpoint();
		return new ImageEndpointsResponse(
			e.getId(),
			e.getTag(),
			e.getPath(),
			e.getMethod(),
			e.getSummary(),
			mapping.getIsChanged(),   // 또는 mapping.isChanged() 등 실제 필드명에 맞게
			mapping.getIsLinked(),
			mapping.getX(),
			mapping.getY()
		);
	}
}
