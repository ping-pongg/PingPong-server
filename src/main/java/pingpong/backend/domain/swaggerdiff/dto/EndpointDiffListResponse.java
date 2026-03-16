package pingpong.backend.domain.swaggerdiff.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "diff 기반 엔드포인트 리스트 응답")
public record EndpointDiffListResponse(

	@JsonProperty("ADDED")
	@Schema(description = "추가된 엔드포인트 (태그별 그룹)")
	List<TagGroupDto> added,

	@JsonProperty("MODIFIED")
	@Schema(description = "수정된 엔드포인트 (태그별 그룹)")
	List<TagGroupDto> modified,

	@JsonProperty("REMOVED")
	@Schema(description = "삭제된 엔드포인트 (태그별 그룹)")
	List<TagGroupDto> removed,

	@JsonProperty("UNCHANGED")
	@Schema(description = "변경 없는 엔드포인트 (태그별 그룹)")
	List<TagGroupDto> unchanged
) {

	@Schema(description = "태그 단위 엔드포인트 그룹")
	public record TagGroupDto(

		@Schema(description = "태그명")
		String tag,

		@Schema(description = "해당 그룹의 엔드포인트 목록")
		List<EndpointSummaryDto> endpoints
	) {
	}
}
