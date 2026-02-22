package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pingpong.backend.domain.notion.dto.response.DatabaseWithPagesResponse;
import pingpong.backend.domain.notion.dto.response.PageDetailResponse;
import pingpong.backend.global.rag.indexing.dto.IndexJob;
import pingpong.backend.global.rag.indexing.enums.IndexSourceType;
import pingpong.backend.global.rag.indexing.job.IndexJobPublisher;
import pingpong.backend.global.rag.indexing.repository.VectorStoreGateway;

/**
 * Notion 웹훅 이벤트 발생 시 VectorDB를 동기화합니다.
 *
 * - 페이지 변경 이벤트(page.content_updated 등): 해당 페이지 + primary database 재인덱싱
 * - page.deleted 이벤트: 해당 페이지 청크 삭제 + primary database 재인덱싱
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotionWebhookIndexingService {

    private final NotionPageService notionPageService;
    private final NotionDatabaseQueryService notionDatabaseQueryService;
    private final IndexJobPublisher indexJobPublisher;
    private final VectorStoreGateway vectorStoreGateway;
    private final ObjectMapper objectMapper;

    @Async("indexExecutor")
    public void triggerPageIndexing(Long teamId, String pageId) {
        log.info("WEBHOOK_INDEX: 페이지 재인덱싱 시작 teamId={} pageId={}", teamId, pageId);
        try {
            indexPage(teamId, pageId);
        } catch (Exception e) {
            log.error("WEBHOOK_INDEX: 페이지 인덱싱 실패 teamId={} pageId={}", teamId, pageId, e);
        }
        try {
            indexPrimaryDatabase(teamId);
        } catch (Exception e) {
            log.error("WEBHOOK_INDEX: primary database 재인덱싱 실패 teamId={} pageId={}", teamId, pageId, e);
        }
    }

    @Async("indexExecutor")
    public void triggerPageDeletion(Long teamId, String pageId) {
        log.info("WEBHOOK_INDEX: 페이지 삭제 처리 시작 teamId={} pageId={}", teamId, pageId);
        try {
            vectorStoreGateway.deleteByPageId(teamId, pageId);
        } catch (Exception e) {
            log.error("WEBHOOK_INDEX: 페이지 벡터 삭제 실패 teamId={} pageId={}", teamId, pageId, e);
        }
        try {
            indexPrimaryDatabase(teamId);
        } catch (Exception e) {
            log.error("WEBHOOK_INDEX: primary database 재인덱싱 실패 teamId={} pageId={}", teamId, pageId, e);
        }
    }

    private void indexPage(Long teamId, String pageId) {
        PageDetailResponse pageResponse = notionPageService.getPageBlocks(teamId, pageId);
        String apiPath = "GET /api/v1/teams/" + teamId + "/notion/pages/" + pageId;
        JsonNode payload = objectMapper.valueToTree(pageResponse);
        indexJobPublisher.publish(new IndexJob(IndexSourceType.NOTION, teamId, apiPath, pageId, payload));
    }

    private void indexPrimaryDatabase(Long teamId) {
        DatabaseWithPagesResponse dbResponse = notionDatabaseQueryService.queryPrimaryDatabase(teamId);
        String apiPath = "GET /api/v1/teams/" + teamId + "/notion/databases/primary";
        JsonNode payload = objectMapper.valueToTree(dbResponse);
        indexJobPublisher.publish(new IndexJob(IndexSourceType.NOTION, teamId, apiPath, null, payload));
    }
}
