package pingpong.backend.domain.swagger.dto;

import pingpong.backend.domain.swagger.SwaggerEndpointSecurity;

public record SnapshotSecurity(
	String type,
	String scheme,
	String headerName,
	String bearerFormat
) {

	public static SnapshotSecurity from(
		SwaggerEndpointSecurity s
	) {
		return new SnapshotSecurity(
			s.getType(),
			s.getScheme(),
			s.getHeaderName(),
			s.getBearerFormat()
		);
	}
}