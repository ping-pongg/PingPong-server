package pingpong.backend.domain.flow.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import pingpong.backend.domain.flow.dto.ImageUploadDto;

@Schema(description = "플로우 생성 응답")
public record FlowCreateResponse(

	@Schema(description = "생성된 플로우 ID", example = "1")
	Long flowId,

	@Schema(description="업로드된 이미지 dto 리스트")
	List<ImageUploadDto> images
) {}
