package pingpong.backend.domain.flow.dto.request;

import jakarta.validation.constraints.Size;

public record FlowImageUploadCompleteRequest(
	Long imageId,
	// (선택) FE가 업로드에 사용한 objectKey를 보내면, 서버 DB의 objectKey와 일치하는지 교차검증 가능
	@Size(max = 1024)
	String expectedObjectKey
) {}
