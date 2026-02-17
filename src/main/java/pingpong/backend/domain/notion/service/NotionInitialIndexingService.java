package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pingpong.backend.domain.notion.dto.response.DatabaseWithPagesResponse;
import pingpong.backend.domain.notion.dto.response.PageSummary;
import pingpong.backend.domain.notion.event.NotionInitialIndexEvent;
import pingpong.backend.global.rag.indexing.dto.IndexJob;
import pingpong.backend.global.rag.indexing.enums.IndexSourceType;
import pingpong.backend.global.rag.indexing.job.IndexJobPublisher;

/**
 * OAuth 교환 트랜잭션 커밋 후 VectorDB 초기 적재를 비동기로 수행합니다.
 *
 * 실행 순서:
 *   1. primary database 전체 조회 → IndexJob publish
 *   2. pages 배열의 각 pageId로 페이지 상세 조회 → IndexJob publish
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotionInitialIndexingService {

    private final NotionDatabaseQueryService notionDatabaseQueryService;
    private final NotionPageService notionPageService;
    private final IndexJobPublisher indexJobPublisher;
    private final ObjectMapper objectMapper;

    @Async("indexExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotionConnected(NotionInitialIndexEvent event) {
        Long teamId = event.teamId();
        log.info("INITIAL_INDEX: 초기 적재 시작 teamId={}", teamId);

        try {
            indexPrimaryDatabase(teamId);
        } catch (Exception e) {
            log.error("INITIAL_INDEX: primary database 적재 실패 teamId={}", teamId, e);
        }
    }

    private void indexPrimaryDatabase(Long teamId) {
        DatabaseWithPagesResponse dbResponse = notionDatabaseQueryService.queryPrimaryDatabase(teamId);

        String dbApiPath = "GET /api/v1/teams/" + teamId + "/notion/databases/primary";
        JsonNode dbPayload = objectMapper.valueToTree(dbResponse);
        indexJobPublisher.publish(new IndexJob(IndexSourceType.NOTION, teamId, dbApiPath, null, dbPayload));
        log.info("INITIAL_INDEX: database 인덱싱 완료 teamId={}", teamId);

        if (dbResponse.pages() == null || dbResponse.pages().isEmpty()) {
            log.info("INITIAL_INDEX: 페이지 없음, 종료 teamId={}", teamId);
            return;
        }

        for (PageSummary page : dbResponse.pages()) {
            if (page.id() == null || page.id().isBlank()) {
                continue;
            }
            indexPage(teamId, page.id());
        }
        log.info("INITIAL_INDEX: 초기 적재 완료 teamId={} pageCount={}", teamId, dbResponse.pages().size());
    }

    private void indexPage(Long teamId, String pageId) {
        try {
            var pageResponse = notionPageService.getPageBlocks(teamId, pageId);
            String pageApiPath = "GET /api/v1/teams/" + teamId + "/notion/pages/" + pageId;
            JsonNode pagePayload = objectMapper.valueToTree(pageResponse);
            indexJobPublisher.publish(new IndexJob(IndexSourceType.NOTION, teamId, pageApiPath, pageId, pagePayload));
        } catch (Exception e) {
            log.warn("INITIAL_INDEX: 페이지 인덱싱 실패 teamId={} pageId={}", teamId, pageId, e);
        }
    }
}
