package pingpong.backend.domain.notion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.notion.repository.NotionRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Notion 웹훅 처리 서비스.
 *
 * 노션이 보내는 두 가지 요청을 처리합니다.
 * - 구독 검증: {@code { "verification_token": "..." }} 단일 필드 페이로드 → challenge 반환
 * - 일반 이벤트: {@code X-Notion-Signature: sha256=<hex>} 헤더 → HMAC-SHA256 검증 후 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotionWebhookService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final ObjectMapper objectMapper;
    private final NotionRepository notionRepository;

    /**
     * 웹훅 요청을 분기하여 처리합니다.
     *
     * @param rawBody         원본 요청 바디 문자열 (서명 재계산용)
     * @param signatureHeader {@code X-Notion-Signature} 헤더 값
     * @return 구독 검증 흐름이면 challenge 문자열({@code Optional.of}), 이벤트 흐름이면 {@code Optional.empty()}
     */
    @Transactional
    public Optional<String> handle(String rawBody, String signatureHeader) {
        JsonNode payload = parseJson(rawBody);
        if (payload == null) {
            return Optional.empty();
        }

        if (payload.has("verification_token")) {
            return Optional.of(payload.get("verification_token").asText());
        }

        processEvent(payload, rawBody, signatureHeader);
        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // private 처리 메서드 (공통 트랜잭션 내에서 실행됨)
    // -------------------------------------------------------------------------

    private void processEvent(JsonNode payload, String rawBody, String signatureHeader) {
        String workspaceId = payload.has("workspace_id") ? payload.get("workspace_id").asText() : null;

        if (!verifySignature(workspaceId, rawBody, signatureHeader)) {
            log.warn("Notion 웹훅 서명 불일치 — 이벤트 무시. workspaceId={}", workspaceId);
            return;
        }

        log.info("Notion 웹훅 이벤트 수신. workspaceId={}, type={}",
                workspaceId,
                payload.has("type") ? payload.get("type").asText() : "unknown");
    }

    private boolean verifySignature(String workspaceId, String rawBody, String signatureHeader) {
        if (workspaceId == null || signatureHeader == null) {
            return false;
        }

        return notionRepository.findByWorkspaceId(workspaceId)
                .map(notion -> notion.getVerificationToken())
                .map(token -> {
                    String expected = SIGNATURE_PREFIX + computeHmacSha256(token, rawBody);
                    return timingSafeEqual(expected, signatureHeader);
                })
                .orElseGet(() -> {
                    log.warn("저장된 verification_token 없음 — 서명 검증 불가. workspaceId={}", workspaceId);
                    return false;
                });
    }

    private JsonNode parseJson(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (JsonProcessingException e) {
            log.warn("Notion 웹훅: JSON 파싱 실패 — 요청 무시.", e);
            return null;
        }
    }

    private String computeHmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 계산 실패", e);
        }
    }

    private boolean timingSafeEqual(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
