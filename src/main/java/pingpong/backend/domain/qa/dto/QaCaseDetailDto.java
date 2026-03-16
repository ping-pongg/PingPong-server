package pingpong.backend.domain.qa.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.swagger.enums.CrudMethod;
import pingpong.backend.domain.swaggerdiff.dto.EndpointParameterDto;

import java.util.List;
import java.util.Map;

@Schema(description = "QA 케이스 단건 상세 (엔드포인트 스키마 + QA 실제 값)")
public record QaCaseDetailDto(

	@Schema(description = "QA 케이스 ID")
	Long qaId,

	@Schema(description = "엔드포인트 ID")
	Long endpointId,

	@Schema(description = "태그명")
	String tag,

	@Schema(description = "엔드포인트 경로")
	String path,

	@Schema(description = "HTTP 메서드")
	CrudMethod method,

	@Schema(description = "QA 케이스 설명")
	String description,

	@Schema(description = "성공 여부")
	Boolean isSuccess,

	@Schema(description = "파라미터 스키마 목록")
	List<EndpointParameterDto> parameters,

	@Schema(description = "요청 본문 스키마 목록")
	List<EndpointRequestBodyDto> requests,

	@Schema(description = "응답 스키마 목록")
	List<EndpointResponseDto> responses,

	@Schema(description = "보안 설정 목록")
	List<EndpointSecurityDto> security,

	@Schema(description = "QA 실제 테스트 데이터")
	QaData qaData,

	@Schema(description = "가장 최근 실행 결과 (실행 이력 없으면 null)")
	QaExecuteResultDto latestExecuteResult
) {

	@Schema(description = "QA 케이스의 실제 테스트 값")
	public record QaData(

		@Schema(description = "경로 변수 실제 값")
		Map<String, String> pathVariables,

		@Schema(description = "쿼리 파라미터 실제 값")
		Map<String, String> queryParams,

		@Schema(description = "헤더 실제 값")
		Map<String, String> headers,

		@Schema(description = "요청 본문 실제 값")
		JsonNode body
	) {
	}
}
