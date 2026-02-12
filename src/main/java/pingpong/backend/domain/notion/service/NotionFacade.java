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
import pingpong.backend.domain.notion.dto.NotionOAuthExchangeResponse;
import pingpong.backend.domain.notion.dto.NotionOAuthTokenResponse;
import pingpong.backend.domain.notion.client.NotionOauthClient;
import pingpong.backend.domain.notion.repository.NotionRepository;
import pingpong.backend.domain.notion.util.NotionJsonUtils;
import pingpong.backend.global.exception.CustomException;

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
