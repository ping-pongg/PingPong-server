package pingpong.backend.domain.flow.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "flow 이미지에 request 생성")
public record FlowRequestCreateRequest(

	@Schema(description = "요청 내용", example = "이메일, 이름 필드 반환 필요합니다")
	String content,

	@Schema(description = "이미지 내 X 좌표", example = "0.1")
	Float x,

	@Schema(description = "이미지 내 Y 좌표", example = "0.1")
	Float y
) {}
