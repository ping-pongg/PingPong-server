package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.notion.Notion;
import pingpong.backend.domain.notion.NotionErrorCode;
import pingpong.backend.domain.notion.dto.response.ChildDatabaseWithPagesResponse;
import pingpong.backend.domain.notion.dto.response.NotionOAuthExchangeResponse;
import pingpong.backend.domain.notion.dto.response.NotionOAuthTokenResponse;
import pingpong.backend.domain.notion.client.NotionOauthClient;
import pingpong.backend.domain.notion.dto.request.NotionCreatePageRequest;
import pingpong.backend.domain.notion.dto.request.NotionPageUpdateRequest;
import pingpong.backend.domain.notion.dto.response.DatabaseCreatedResponse;
import pingpong.backend.domain.notion.dto.response.DatabaseWithPagesResponse;
import pingpong.backend.domain.notion.dto.response.PageDetailResponse;
import pingpong.backend.domain.notion.event.NotionInitialIndexEvent;
import pingpong.backend.domain.notion.repository.NotionRepository;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.global.exception.CustomException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionFacade {

    private final NotionOauthClient notionOauthClient;
    private final NotionTokenService notionTokenService;
    private final NotionConnectionService notionConnectionService;
    private final NotionConnectionApiService notionConnectionApiService;
    private final NotionDatabaseQueryService notionDatabaseQueryService;
    private final NotionPageService notionPageService;
    private final NotionDatabaseCreateService notionDatabaseCreateService;
    private final NotionWebhookIndexingService notionWebhookIndexingService;
    private final NotionRepository notionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public NotionOAuthExchangeResponse exchangeCodeAndPersist(Long teamId, Member member, String code) {
        notionConnectionService.assertTeamAccess(teamId, member);

        NotionOAuthTokenResponse tokenResponse;
        try {
            tokenResponse = notionOauthClient.exchangeAuthorizationCode(code);
        } catch (Exception e) {
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }
        Notion notion = notionTokenService.upsertTokensFromOauth(teamId, tokenResponse);

        return new NotionOAuthExchangeResponse(
                true,
                notion.getWorkspaceId(),
                notion.getWorkspaceName(),
                notion.getDatabaseId(),
                notion.isDatabaseSelected()
        );
    }

    @Transactional
    public void refreshTokens(Long teamId, Member member) {
        notionConnectionService.assertTeamAccess(teamId, member);
        notionTokenService.refreshTokens(teamId);
    }

    public JsonNode listCandidateDatabases(Long teamId, Member member) {
        notionConnectionService.assertTeamAccess(teamId, member);
        return notionConnectionApiService.listCandidateDatabases(teamId);
    }

    @Transactional
    public void setPrimaryDatabase(Long teamId, Member member, String databaseId) {
        notionConnectionService.assertTeamAccess(teamId, member);
        notionConnectionApiService.connectDatabase(teamId, databaseId);
        eventPublisher.publishEvent(new NotionInitialIndexEvent(teamId));
    }

    public DatabaseWithPagesResponse queryPrimaryDatabase(Long teamId, Member member) {
        notionConnectionService.assertTeamAccess(teamId, member);
        return notionDatabaseQueryService.queryPrimaryDatabase(teamId);
    }

    public PageDetailResponse createPageInPrimaryDatabase(Long teamId, Member member, NotionCreatePageRequest request) {
        notionConnectionService.assertTeamAccess(teamId, member);
        String databaseId = notionConnectionService.resolveConnectedDatabaseId(teamId);
        PageDetailResponse result = notionPageService.createPage(teamId, databaseId, request);
        notionWebhookIndexingService.triggerAfterPageCreate(teamId, result);
        return result;
    }

    public PageDetailResponse updatePage(Long teamId, Member member, String pageId, NotionPageUpdateRequest request) {
        notionConnectionService.assertTeamAccess(teamId, member);
        return notionPageService.updatePage(teamId, pageId, request);
    }

    public PageDetailResponse getPageBlocks(Long teamId, Member member, String pageId) {
        notionConnectionService.assertTeamAccess(teamId, member);
        return notionPageService.getPageBlocks(teamId, pageId);
    }

    /**
     * Task의 flowMappingCompleted = true 설정 시 호출.
     * 기존 child DB가 있으면 archived 처리 후 새 DB를 생성하고, 각 endpoint 행을 추가한다.
     *
     * @param teamId                 팀 ID
     * @param taskPageId             Task의 Notion 페이지 ID (child DB의 parent)
     * @param existingChildDatabaseId 기존 child DB ID (없으면 null)
     * @param endpoints              추가할 endpoint 목록
     * @return 새로 생성된 DB ID
     */
    public String setupTaskDatabase(Long teamId, String taskPageId, String existingChildDatabaseId, List<Endpoint> endpoints) {
        if (existingChildDatabaseId != null) {
            notionDatabaseCreateService.archiveBlock(teamId, existingChildDatabaseId);
        }

        DatabaseCreatedResponse created = notionDatabaseCreateService.createDatabase(teamId, taskPageId);
        String newDbId = created.id();

        for (Endpoint endpoint : endpoints) {
            String apiListValue = endpoint.getMethod().name() + " " + endpoint.getPath();
            notionDatabaseCreateService.addRowToDatabase(teamId, newDbId, apiListValue);
        }

        notionWebhookIndexingService.triggerAfterDatabaseCreate(teamId, newDbId, taskPageId);
        return newDbId;
    }

    /**
     * Task child database 내 특정 endpoint 행의 Status(select)를 업데이트한다.
     * 대상 페이지는 "API List" title 값으로 탐색한다.
     * Notion 호출 실패 시 예외를 전파하지 않고 경고 로그만 기록한다.
     *
     * @param teamId          팀 ID
     * @param childDatabaseId child database ID
     * @param apiListValue    "METHOD /path" 형식의 API List 값 (예: "GET /api/v1/users")
     * @param newStatus       새 상태 ("Backend" / "Frontend" / "Complete")
     */
    public void updateChildDatabaseEndpointStatus(
            Long teamId, String childDatabaseId, String apiListValue, String newStatus) {
        ChildDatabaseWithPagesResponse dbResponse;
        try {
            dbResponse = notionDatabaseQueryService.queryChildDatabase(teamId, childDatabaseId);
        } catch (Exception e) {
            log.warn("ENDPOINT-STATUS-SYNC: child DB 조회 실패 — databaseId={} error={}",
                    childDatabaseId, e.getMessage());
            return;
        }

        dbResponse.pages().stream()
                .filter(page -> apiListValue.equals(page.title()))
                .findFirst()
                .ifPresentOrElse(
                        page -> {
                            try {
                                notionDatabaseCreateService.updateRowStatus(teamId, page.id(), newStatus);
                                log.info("ENDPOINT-STATUS-SYNC: 완료 — databaseId={} apiList={} newStatus={}",
                                        childDatabaseId, apiListValue, newStatus);
                            } catch (Exception e) {
                                log.warn("ENDPOINT-STATUS-SYNC: 페이지 업데이트 실패 — pageId={} error={}",
                                        page.id(), e.getMessage());
                            }
                        },
                        () -> log.warn("ENDPOINT-STATUS-SYNC: 해당 endpoint row 없음 — databaseId={} apiList={}",
                                childDatabaseId, apiListValue)
                );
    }

    @Transactional(readOnly = true)
    public NotionOAuthExchangeResponse getNotionStatus(Long teamId, Member member) {
        notionConnectionService.assertTeamAccess(teamId, member);
        return notionRepository.findByTeamId(teamId)
                .filter(n -> n.getAccessToken() != null)
                .map(n -> new NotionOAuthExchangeResponse(
                        true,
                        n.getWorkspaceId(),
                        n.getWorkspaceName(),
                        n.getDatabaseId(),
                        n.isDatabaseSelected()
                ))
                .orElse(new NotionOAuthExchangeResponse(false, null, null, null, false));
    }

}
