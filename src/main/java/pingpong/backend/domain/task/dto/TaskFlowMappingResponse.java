package pingpong.backend.domain.task.dto;

import java.util.List;

public record TaskFlowMappingResponse(String taskId, List<Long> flowIds) {}
