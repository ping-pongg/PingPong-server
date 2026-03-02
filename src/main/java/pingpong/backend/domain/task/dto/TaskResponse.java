package pingpong.backend.domain.task.dto;

import pingpong.backend.domain.task.Task;

import java.time.Instant;

public record TaskResponse(
        String id,
        String url,
        String title,
        String dateStart,
        String dateEnd,
        String status,
        Instant lastSyncedAt
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getUrl(),
                task.getTitle(),
                task.getDateStart(),
                task.getDateEnd(),
                task.getStatus(),
                task.getLastSyncedAt()
        );
    }
}
