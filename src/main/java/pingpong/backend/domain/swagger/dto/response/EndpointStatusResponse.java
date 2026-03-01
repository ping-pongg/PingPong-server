package pingpong.backend.domain.swagger.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.flow.enums.FlowEndpointLinkStatus;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.enums.CrudMethod;

@Schema(description = "endpoint 전부를 보여줍니다")
public record EndpointStatusResponse(

	@Schema(description = "endpoint ID")
	Long endpointId,

	@Schema(description = "endpoint 연동 상태")
	FlowEndpointLinkStatus status,

	@Schema(description = "태그명")
	String tag,

	@Schema(description = "엔드포인트 path")
	String path,

	@Schema(description = "method")
	CrudMethod method,

	@Schema(description = "엔드포인트 요약")
	String summary

) {

	public static EndpointStatusResponse of(
		Endpoint endpoint,
		FlowEndpointLinkStatus status
	) {
		return new EndpointStatusResponse(
			endpoint.getId(),
			status,
			endpoint.getTag(),
			endpoint.getPath(),
			endpoint.getMethod(),
			endpoint.getSummary()
		);
	}
}