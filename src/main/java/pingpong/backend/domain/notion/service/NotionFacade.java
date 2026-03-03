package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.notion.Notion;
import pingpong.backend.domain.notion.NotionErrorCode;
import pingpong.backend.domain.notion.client.NotionRestClient;
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
import pingpong.backend.domain.notion.util.NotionJsonUtils;
import pingpong.backend.domain.swagger.Endpoint;
import pingpong.backend.global.exception.CustomException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotionFacade {

    private static final int MAX_RETRIES = 2;

    private final NotionOauthClient notionOauthClient;
    private final NotionRestClient notionRestClient;
    private final NotionTokenService notionTokenService;
    private final NotionConnectionService notionConnectionService;
    private final NotionConnectionApiService notionConnectionApiService;
    private final NotionDatabaseQueryService notionDatabaseQueryService;
    private final NotionPageService notionPageService;
    private final NotionDatabaseCreateService notionDatabaseCreateService;
    private final NotionWebhookIndexingService notionWebhookIndexingService;
    private final NotionRepository notionRepository;
    private final NotionJsonUtils notionJsonUtils;
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

        if (notion.getDatabaseId() == null) {
            notion = resolveAndPersistDatabaseId(teamId);
        }

        return new NotionOAuthExchangeResponse(
                true,
                notion.getWorkspaceId(),
                notion.getWorkspaceName(),
                notion.getDatabaseId(),
                notion.getDatabaseId() != null
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
                        n.getDatabaseId() != null
                ))
                .orElse(new NotionOAuthExchangeResponse(false, null, null, null, false));
    }

    private Notion resolveAndPersistDatabaseId(Long teamId) {
        Notion notion = notionTokenService.getNotionOrThrow(teamId);

        Map<String, Object> filter = new HashMap<>();
        filter.put("property", "object");
        filter.put("value", "database");

        Map<String, Object> body = new HashMap<>();
        body.put("filter", filter);
        body.put("page_size", 1);

        ResponseEntity<String> response = notionTokenService.executeWithRefreshAndRetry(teamId,
                () -> notionRestClient.post("/v1/search", notionTokenService.getAccessToken(teamId), body),
                MAX_RETRIES,
                200);

        JsonNode json = notionJsonUtils.parseJson(response);
        String databaseId = null;
        if (json != null && json.has("results") && json.get("results").isArray() && !json.get("results").isEmpty()) {
            databaseId = json.get("results").get(0).path("id").asText(null);
        }

        notion.updateDatabaseId(databaseId);
        return notionRepository.save(notion);
    }
}
