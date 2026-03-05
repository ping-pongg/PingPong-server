package pingpong.backend.domain.swagger.dto.response;

import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.enums.CrudMethod;

public record EndpointSearchResponse(
	Long id,
	String path,
	CrudMethod method,
	String summary
) {
	public static EndpointSearchResponse toDto(Endpoint e) {
		return new EndpointSearchResponse(e.getId(), e.getPath(), e.getMethod(), e.getSummary());
	}
}
