package pingpong.backend.domain.flow.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "플로우 생성 응답")
public record FlowCreateResponse(

	@Schema(description = "생성된 플로우 ID", example = "1")
	Long flowId
) {}
