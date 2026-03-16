package pingpong.backend.domain.swaggerdiff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.domain.swagger.enums.CrudMethod;

import java.util.Optional;

@Schema(description = "엔드포인트 요약 정보 (리스트용)")
public record EndpointSummaryDto(

	@Schema(description = "엔드포인트 ID")
	Long endpointId,

	@Schema(description = "태그명")
	String tag,

	@Schema(description = "엔드포인트 경로")
	String path,

	@Schema(description = "HTTP 메서드")
	CrudMethod method,

	@Schema(description = "엔드포인트 요약")
	String summary
) {

	public static EndpointSummaryDto from(Endpoint e) {
		return new EndpointSummaryDto(
			e.getId(),
			Optional.ofNullable(e.getTag()).orElse("default"),
			e.getPath(),
			e.getMethod(),
			e.getSummary()
		);
	}
}
