package pingpong.backend.domain.qa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.SwaggerEndpointSecurity;

@Schema(description = "엔드포인트 보안 설정 정보")
public record EndpointSecurityDto(

	@Schema(description = "인증 타입 (예: http)")
	String type,

	@Schema(description = "인증 방식 (예: bearer)")
	String scheme,

	@Schema(description = "헤더 이름")
	String headerName,

	@Schema(description = "Bearer 포맷 (예: JWT)")
	String bearerFormat
) {

	public static EndpointSecurityDto fromEntity(SwaggerEndpointSecurity s) {
		if (s == null) return null;
		return new EndpointSecurityDto(
			s.getType(),
			s.getScheme(),
			s.getHeaderName(),
			s.getBearerFormat()
		);
	}
}
