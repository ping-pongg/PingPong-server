package pingpong.backend.domain.flow.dto.response;

import pingpong.backend.domain.flow.UploadStatus;

public record FlowImageResponse(
	Long imageId,
	String imageUrl,   // GET presigned
	UploadStatus status
) {}