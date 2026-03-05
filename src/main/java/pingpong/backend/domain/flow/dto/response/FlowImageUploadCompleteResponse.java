package pingpong.backend.domain.flow.dto.response;

import pingpong.backend.domain.flow.enums.BulkCompleteResultStatus;
import pingpong.backend.domain.flow.enums.UploadStatus;

public record FlowImageUploadCompleteResponse(
	Long imageId,
	UploadStatus uploadStatus,      // FlowImage의 실제 상태(PENDING/COMPLETE/FAILED 등)
	String objectKey
) {


}
