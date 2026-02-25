package pingpong.backend.domain.swagger.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.enums.CrudMethod;

@Schema(description = "endpoint 전부를 보여줍니다")
public record EndpointResponse (

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

	@Schema(description="endpoint 변화 여부")
	Boolean isChanged

){

	public static EndpointResponse toDto(Endpoint e){
		return new EndpointResponse(e.getId(),e.getTag(),e.getPath(), e.getMethod(), e.getSummary(), e.getIsChanged());
	}
}
