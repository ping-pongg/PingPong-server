package pingpong.backend.domain.task.dto;

import pingpong.backend.domain.flow.Flow;
import pingpong.backend.domain.task.Task;

import java.time.Instant;
import java.util.List;

public record TaskResponse(
        String id,
        String url,
        String title,
        String dateStart,
        String dateEnd,
        String completedDateStart,
        String completedDateEnd,
        String status,
        Instant lastSyncedAt,
        Boolean flowMappingCompleted,
        List<FlowInfo> flows
) {
    public record FlowInfo(Long id, String title, String description) {
        public static FlowInfo from(Flow flow) {
            return new FlowInfo(flow.getId(), flow.getTitle(), flow.getDescription());
        }
    }

    public static TaskResponse from(Task task, List<FlowInfo> flows) {
        return new TaskResponse(
                task.getId(),
                task.getUrl(),
                task.getTitle(),
                task.getDateStart(),
                task.getDateEnd(),
                task.getCompletedDateStart(),
                task.getCompletedDateEnd(),
                task.getStatus(),
                task.getLastSyncedAt(),
                task.getFlowMappingCompleted(),
                flows
        );
    }
}
