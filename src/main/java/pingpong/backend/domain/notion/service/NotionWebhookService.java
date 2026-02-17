package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.notion.Notion;
import pingpong.backend.domain.notion.repository.NotionRepository;

import java.util.Optional;
import java.util.Set;

/**
 * Notion 웹훅 처리 서비스.
 *
 * 노션이 보내는 두 가지 요청을 처리합니다.
 * - 구독 검증: {@code { "verification_token": "..." }} 단일 필드 페이로드 → challenge 반환
 * - 일반 이벤트: page.* 이벤트 감지 → VectorDB 동기화 비동기 위임
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotionWebhookService {

    private static final Set<String> PAGE_EVENT_TYPES = Set.of(
            "page.content_updated",
            "page.created",
            "page.deleted",
            "page.locked",
            "page.moved",
            "page.properties_updated",
            "page.undeleted",
            "page.unlocked"
    );

    private final ObjectMapper objectMapper;
    private final NotionRepository notionRepository;
    private final NotionWebhookIndexingService notionWebhookIndexingService;

    /**
     * 웹훅 요청을 분기하여 처리합니다.
     *
     * @param rawBody         원본 요청 바디 문자열
     * @return 구독 검증 흐름이면 challenge 문자열({@code Optional.of}), 이벤트 흐름이면 {@code Optional.empty()}
     */
    @Transactional(readOnly = true)
    public Optional<String> handle(String rawBody) {
        JsonNode payload = parseJson(rawBody);
        if (payload == null) {
            return Optional.empty();
        }

        if (payload.has("verification_token")) {
            String token = payload.get("verification_token").asText();
            log.info("Notion webhook verification_token received: {}", token);
            return Optional.of(token);
        }

        processEvent(payload);
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // private 처리 메서드
    // -------------------------------------------------------------------------

    private void processEvent(JsonNode payload) {
        String type = payload.has("type") ? payload.get("type").asText("") : "";
        String workspaceId = payload.has("workspace_id") ? payload.get("workspace_id").asText() : null;
        log.info("Notion 웹훅 이벤트 수신. workspaceId={} type={}", workspaceId, type);

        if (!PAGE_EVENT_TYPES.contains(type)) {
            return;
        }

        JsonNode entity = payload.path("entity");
        String entityType = entity.path("type").asText("");
        if (!"page".equals(entityType)) {
            return;
        }

        String pageId = entity.path("id").asText("").replace("-", "");
        if (pageId.isBlank()) {
            log.warn("WEBHOOK: page 이벤트에서 entity.id 추출 실패. type={}", type);
            return;
        }

        if (workspaceId == null || workspaceId.isBlank()) {
            log.warn("WEBHOOK: workspace_id 없음. type={}", type);
            return;
        }

        Optional<Notion> notionOpt = notionRepository.findByWorkspaceId(workspaceId);
        if (notionOpt.isEmpty()) {
            log.warn("WEBHOOK: workspace_id={}에 해당하는 Notion 연동 정보 없음.", workspaceId);
            return;
        }

        Long teamId = notionOpt.get().getTeam().getId();

        if ("page.deleted".equals(type)) {
            notionWebhookIndexingService.triggerPageDeletion(teamId, pageId);
        } else {
            notionWebhookIndexingService.triggerPageIndexing(teamId, pageId);
        }
    }

    private JsonNode parseJson(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (JsonProcessingException e) {
            log.warn("Notion 웹훅: JSON 파싱 실패 — 요청 무시.", e);
            return null;
        }
    }
}
