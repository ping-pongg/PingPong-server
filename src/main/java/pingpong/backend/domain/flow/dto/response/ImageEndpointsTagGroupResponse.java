package pingpong.backend.domain.flow.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tag별로 묶인 flow 이미지 endpoint 목록")
public record ImageEndpointsTagGroupResponse(

	@Schema(description = "태그명 (null 가능)")
	String tag,

	@Schema(description = "해당 태그의 endpoint 목록")
	List<ImageEndpointsResponse> endpoints

) {}
