package pingpong.backend.domain.flow.dto.response;

import java.util.List;

public record FlowImageUploadCompleteBulkResponse(
	int totalCount,
	int successCount,
	int failCount,
	List<FlowImageUploadCompleteResponse> results
) {}
