package pingpong.backend.domain.qa.dto;

import java.util.List;

public record QaBulkExecuteResponse(
	int totalCount,
	int successCount,
	int failCount,
	List<QaExecuteResultDto> results
) {}
