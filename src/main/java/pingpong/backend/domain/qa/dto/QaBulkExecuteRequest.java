package pingpong.backend.domain.qa.dto;

import java.util.List;

public record QaBulkExecuteRequest(
	List<Long> qaIds
) {}