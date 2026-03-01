package pingpong.backend.domain.swagger.dto.response;

import pingpong.backend.domain.swagger.SwaggerEndpointSecurity;

public record SnapshotSecurityResponse(

	String type,
	String scheme,
	String headerName,
	String bearerFormat

) {

	public static SnapshotSecurityResponse from(
		SwaggerEndpointSecurity s
	) {
		if (s == null) return null;

		return new SnapshotSecurityResponse(
			s.getType(),
			s.getScheme(),
			s.getHeaderName(),
			s.getBearerFormat()
		);
	}
}