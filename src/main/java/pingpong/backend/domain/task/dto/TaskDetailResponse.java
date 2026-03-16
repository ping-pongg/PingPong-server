package pingpong.backend.domain.task.dto;

import java.util.List;

public record TaskDetailResponse(
        String taskId,
        String title,
        String status,
        String dateStart,
        String dateEnd,
        String completedDateStart,
        String completedDateEnd,
        String pageContent,
        List<FlowDetail> flows
) {
    public record FlowDetail(
            Long flowId,
            String title,
            String description,
            List<EndpointInfo> endpoints
    ) {}

    public record EndpointInfo(
            Long id,
            String path,
            String method,
            String summary,
            String tag
    ) {}
}
