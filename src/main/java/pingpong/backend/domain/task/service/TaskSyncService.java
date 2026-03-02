package pingpong.backend.domain.task.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.notion.dto.response.PageDetailResponse;
import pingpong.backend.domain.notion.service.NotionConnectionService;
import pingpong.backend.domain.task.Task;
import pingpong.backend.domain.task.repository.FlowTaskRepository;
import pingpong.backend.domain.task.repository.TaskRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSyncService {

    private final TaskRepository taskRepository;
    private final FlowTaskRepository flowTaskRepository;
    private final NotionConnectionService notionConnectionService;

    /**
     * primary database 페이지인 경우 Task를 upsert합니다.
     * parentDatabaseId가 primary database ID와 일치하지 않으면 무시합니다.
     */
    @Transactional
    public void upsert(Long teamId, PageDetailResponse page) {
        log.info("TASK_SYNC: upsert 시작 teamId={} pageId={} parentDatabaseId={}",
                teamId, page.id(), page.parentDatabaseId());

        String primaryDbId = resolvePrimaryDbId(teamId);
        if (primaryDbId == null) {
            log.warn("TASK_SYNC: primaryDbId 조회 실패로 upsert 건너뜀 teamId={} pageId={}", teamId, page.id());
            return;
        }

        String parentDbId = page.parentDatabaseId();
        if (parentDbId == null || !parentDbId.equals(primaryDbId)) {
            log.info("TASK_SYNC: primary database 페이지 아님 — 건너뜀 teamId={} pageId={} parentDbId={} primaryDbId={}",
                    teamId, page.id(), parentDbId, primaryDbId);
            return;
        }

        taskRepository.save(Task.from(teamId, page));
        log.info("TASK_SYNC: upsert 완료 teamId={} pageId={} title={}", teamId, page.id(), page.title());
    }

    /**
     * Task와 연관된 FlowTask를 삭제한 뒤 Task를 hard delete합니다.
     * 존재하지 않는 pageId면 무시합니다.
     */
    @Transactional
    public void delete(String pageId) {
        log.info("TASK_SYNC: delete 시작 pageId={}", pageId);
        flowTaskRepository.deleteAllByTaskId(pageId);
        taskRepository.deleteById(pageId);
        log.info("TASK_SYNC: delete 완료 pageId={}", pageId);
    }

    private String resolvePrimaryDbId(Long teamId) {
        try {
            String databaseId = notionConnectionService.resolveConnectedDatabaseId(teamId);
            log.info("TASK_SYNC: primaryDbId 조회 성공 teamId={} primaryDbId={}", teamId, databaseId);
            return databaseId;
        } catch (Exception e) {
            log.warn("TASK_SYNC: primaryDbId 조회 실패 teamId={} reason={}", teamId, e.getMessage());
            return null;
        }
    }
}
