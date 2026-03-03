package pingpong.backend.domain.flow.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.enums.CrudMethod;

@Schema(description = "flow 이미지에 할당된 엔드포인트 기준 목록")
public record ImageEndpointsResponse(

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

	@Schema(description = "변화 여부")
	Boolean isChanged,

	@Schema(description = "연동 여부")
	Boolean isLinked,

	@Schema(description = "연결된 request 목록")
	List<RequestSummary> requests

) {
	public record RequestSummary(

		@Schema(description = "request ID")
		Long requestId,

		@Schema(description = "요청 내용")
		String content
	) {}
}
