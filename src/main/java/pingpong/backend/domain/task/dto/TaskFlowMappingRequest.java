package pingpong.backend.domain.task.dto;

import java.util.List;

public record TaskFlowMappingRequest(List<Long> flowIds) {}
