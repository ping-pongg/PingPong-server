package pingpong.backend.domain.swagger.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.enums.CrudMethod;

@Schema(description = "endpoint 상세 조회 응답 dto ")
public record EndpointDiffDetailResponse(

	@Schema
	String path,

	@Schema
	CrudMethod method,

	@Schema
	List<ParameterResponse> parameters,

	@Schema
	List<RequestBodyResponse> requests,

	@Schema
	List<ResponseBodyResponse> responses
){
}
