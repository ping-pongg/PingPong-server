package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import pingpong.backend.domain.notion.Notion;
import pingpong.backend.domain.notion.NotionErrorCode;
import pingpong.backend.domain.notion.client.NotionOauthClient;
import pingpong.backend.domain.notion.dto.NotionOAuthTokenResponse;
import pingpong.backend.domain.notion.repository.NotionRepository;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.TeamErrorCode;
import pingpong.backend.domain.team.repository.TeamRepository;
import pingpong.backend.global.exception.CustomException;

import java.time.Instant;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class NotionTokenService {

    private final NotionRepository notionRepository;
    private final TeamRepository teamRepository;
    private final NotionOauthClient notionOauthClient;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Notion getNotionOrThrow(Long teamId) {
        return notionRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(NotionErrorCode.NOTION_NOT_CONNECTED));
    }

    @Transactional
    public Notion upsertTokensFromOauth(Long teamId, NotionOAuthTokenResponse response) {
        if (response == null || response.accessToken() == null || response.refreshToken() == null) {
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException(TeamErrorCode.TEAM_NOT_FOUND));

        Notion notion = notionRepository.findByTeamId(teamId)
                .orElseGet(() -> Notion.create(team));

        notion.updateWorkspace(response.workspaceId(), response.botId(), response.workspaceName());
        notion.updateTokens(response.accessToken(), response.refreshToken(), Instant.now());

        return notionRepository.save(notion);
    }

    @Transactional
    public Notion refreshTokens(Long teamId) {
        Notion notion = notionRepository.findByTeamIdForUpdate(teamId)
                .orElseThrow(() -> new CustomException(NotionErrorCode.NOTION_NOT_CONNECTED));

        String refreshToken = notion.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CustomException(NotionErrorCode.NOTION_TOKEN_REFRESH_FAILED);
        }

        NotionOAuthTokenResponse response;
        try {
            response = notionOauthClient.refreshToken(refreshToken);
        } catch (Exception e) {
            throw new CustomException(NotionErrorCode.NOTION_TOKEN_REFRESH_FAILED);
        }
        if (response == null || response.accessToken() == null || response.refreshToken() == null) {
            throw new CustomException(NotionErrorCode.NOTION_TOKEN_REFRESH_FAILED);
        }

        notion.updateTokens(response.accessToken(), response.refreshToken(), Instant.now());
        return notionRepository.save(notion);
    }

    @Transactional(readOnly = true)
    public String getAccessToken(Long teamId) {
        Notion notion = getNotionOrThrow(teamId);
        String accessToken = notion.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new CustomException(NotionErrorCode.NOTION_NOT_CONNECTED);
        }
        return accessToken;
    }

    public ResponseEntity<String> executeWithRefresh(Long teamId,
                                                     Supplier<ResponseEntity<String>> requestSupplier) {
        ResponseEntity<String> response = requestSupplier.get();
        if (!isAuthError(response)) {
            return response;
        }

        refreshTokens(teamId);
        ResponseEntity<String> retryResponse = requestSupplier.get();
        if (isAuthError(retryResponse)) {
            throw new CustomException(NotionErrorCode.NOTION_PERMISSION_DENIED);
        }
        return retryResponse;
    }

    public ResponseEntity<String> executeWithRefreshAndRetry(Long teamId,
                                                             Supplier<ResponseEntity<String>> requestSupplier,
                                                             int maxRetries,
                                                             long initialBackoffMillis) {
        int attempt = 0;
        long backoffMillis = initialBackoffMillis;
        while (true) {
            attempt++;
            try {
                ResponseEntity<String> response = executeWithRefresh(teamId, requestSupplier);
                if (response.getStatusCode().is5xxServerError() && attempt <= maxRetries) {
                    sleep(backoffMillis);
                    backoffMillis *= 2;
                    continue;
                }
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
                }
                return response;
            } catch (RestClientException e) {
                if (attempt <= maxRetries) {
                    sleep(backoffMillis);
                    backoffMillis *= 2;
                    continue;
                }
                throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
            }
        }
    }

    private boolean isAuthError(ResponseEntity<String> response) {
        if (response == null || response.getStatusCode().value() == 0) {
            return false;
        }
        if (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403) {
            return true;
        }
        return containsInvalidToken(response.getBody());
    }

    private boolean containsInvalidToken(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String code = node.path("code").asText();
            return "invalid_token".equalsIgnoreCase(code);
        } catch (Exception e) {
            return body.contains("invalid_token");
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(NotionErrorCode.NOTION_API_ERROR);
        }
    }
}
