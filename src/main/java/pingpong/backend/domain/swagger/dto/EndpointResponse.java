package pingpong.backend.domain.swagger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.enums.CrudMethod;

@Schema(description = "endpoint 전부를 보여줍니다")
public record EndpointResponse (

	@Schema(description = "엔드포인트 path")
	String path,

	@Schema(description = "method")
	CrudMethod method,

	@Schema(description = "엔드포인트 요약")
	String summary,

	@Schema(description="연동 여부")
	Boolean isChanged

){

	public static EndpointResponse toDto(Endpoint e){
		return new EndpointResponse(e.getPath(), e.getMethod(), e.getSummary(), e.getIsChanged());
	}
}
