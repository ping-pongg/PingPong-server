package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

/**
 * Notion 웹훅 처리 서비스.
 *
 * 노션이 보내는 두 가지 요청을 처리합니다.
 * - 구독 검증: {@code { "verification_token": "..." }} 단일 필드 페이로드 → challenge 반환
 * - 일반 이벤트: 이벤트 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotionWebhookService {

    private final ObjectMapper objectMapper;

    /**
     * 웹훅 요청을 분기하여 처리합니다.
     *
     * @param rawBody         원본 요청 바디 문자열
     * @return 구독 검증 흐름이면 challenge 문자열({@code Optional.of}), 이벤트 흐름이면 {@code Optional.empty()}
     */
    @Transactional
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
    // private 처리 메서드 (공통 트랜잭션 내에서 실행됨)
    // -------------------------------------------------------------------------

    private void processEvent(JsonNode payload) {
        String workspaceId = payload.has("workspace_id") ? payload.get("workspace_id").asText() : null;
        log.info("Notion 웹훅 이벤트 수신. workspaceId={}, type={}",
                workspaceId,
                payload.has("type") ? payload.get("type").asText() : "unknown");
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
