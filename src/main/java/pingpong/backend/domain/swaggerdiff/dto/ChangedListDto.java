package pingpong.backend.domain.swaggerdiff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "리스트 필드의 변경 상세 (추가/삭제 항목)")
public record ChangedListDto(

	@Schema(description = "추가된 항목")
	List<String> added,

	@Schema(description = "삭제된 항목")
	List<String> removed
) {
}
