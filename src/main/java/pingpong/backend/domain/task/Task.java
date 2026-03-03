package pingpong.backend.domain.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pingpong.backend.domain.notion.dto.common.PageDateRange;
import pingpong.backend.domain.notion.dto.response.PageDetailResponse;

import java.time.Instant;

@Entity
@Table(name = "task")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Task {

    @Id
    @Column(name = "notion_page_id")
    private String id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "url")
    private String url;

    @Column(name = "title")
    private String title;

    @Column(name = "date_start")
    private String dateStart;

    @Column(name = "date_end")
    private String dateEnd;

    @Column(name = "status")
    private String status;

    @Column(name = "page_content", columnDefinition = "TEXT")
    private String pageContent;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    @Builder.Default
    @Column(name = "flow_mapping_completed", nullable = false)
    private Boolean flowMappingCompleted = false;

    @Column(name = "child_database_id")
    private String childDatabaseId;

    public void updateFlowMappingCompleted(boolean completed) {
        this.flowMappingCompleted = completed;
    }

    public void updateChildDatabaseId(String id) {
        this.childDatabaseId = id;
    }

    public static Task from(Long teamId, PageDetailResponse page) {
        PageDateRange date = page.date();
        return Task.builder()
                .id(page.id())
                .teamId(teamId)
                .url(page.url())
                .title(page.title())
                .dateStart(date != null ? date.start() : null)
                .dateEnd(date != null ? date.end() : null)
                .status(page.status())
                .pageContent(page.pageContent())
                .lastSyncedAt(Instant.now())
                .build();
    }
}
