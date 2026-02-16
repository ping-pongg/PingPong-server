package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
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
import pingpong.backend.domain.notion.repository.NotionRepository;
import pingpong.backend.domain.notion.util.NotionJsonUtils;
import pingpong.backend.global.annotation.IndexOnRead;
import pingpong.backend.global.exception.CustomException;
import pingpong.backend.global.rag.indexing.enums.IndexSourceType;

import java.util.HashMap;
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
    private final NotionRepository notionRepository;
    private final NotionJsonUtils notionJsonUtils;

    @Transactional
    public NotionOAuthExchangeResponse exchangeCodeAndPersist(Long teamId, Member member, String code, String redirectUri) {
        notionConnectionService.assertTeamAccess(teamId, member);

        NotionOAuthTokenResponse tokenResponse;
        try {
            tokenResponse = notionOauthClient.exchangeAuthorizationCode(code, redirectUri);
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

    public void setPrimaryDatabase(Long teamId, Member member, String databaseId) {
        notionConnectionService.assertTeamAccess(teamId, member);
        notionConnectionApiService.connectDatabase(teamId, databaseId);
    }

    @IndexOnRead(
            sourceType = IndexSourceType.NOTION,
            teamId = "#p0",
            apiPath = "'GET /api/v1/teams/' + #p0 + '/notion/databases/primary'",
            skipIfPageSizePresent = false,
            skipIfStartCursorPresent = false
    )
    public DatabaseWithPagesResponse queryPrimaryDatabase(Long teamId, Member member) {
        notionConnectionService.assertTeamAccess(teamId, member);
        return notionDatabaseQueryService.queryPrimaryDatabase(teamId);
    }

    public PageDetailResponse createPageInPrimaryDatabase(Long teamId, Member member, NotionCreatePageRequest request) {
        notionConnectionService.assertTeamAccess(teamId, member);
        String databaseId = notionConnectionService.resolveConnectedDatabaseId(teamId);
        return notionPageService.createPage(teamId, databaseId, request);
    }

    public PageDetailResponse updatePage(Long teamId, Member member, String pageId, NotionPageUpdateRequest request) {
        notionConnectionService.assertTeamAccess(teamId, member);
        return notionPageService.updatePage(teamId, pageId, request);
    }

    @IndexOnRead(
            sourceType = IndexSourceType.NOTION,
            teamId = "#p0",
            apiPath = "'GET /api/v1/teams/' + #p0 + '/notion/pages/' + #p2",
            resourceId = "#p2",
            skipIfPageSizePresent = false,
            skipIfStartCursorPresent = false
    )
    public PageDetailResponse getPageBlocks(Long teamId, Member member, String pageId) {
        notionConnectionService.assertTeamAccess(teamId, member);
        return notionPageService.getPageBlocks(teamId, pageId);
    }

    public DatabaseCreatedResponse createDatabase(Long teamId, Member member, String parentPageId) {
        notionConnectionService.assertTeamAccess(teamId, member);
        return notionDatabaseCreateService.createDatabase(teamId, parentPageId);
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
