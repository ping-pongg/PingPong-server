package pingpong.backend.domain.notion.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pingpong.backend.domain.notion.service.NotionWebhookService;
import pingpong.backend.global.response.result.SuccessResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notion/webhooks")
@RequiredArgsConstructor
@Tag(name = "Notion Webhook API", description = "Notion 웹훅 구독 검증 및 이벤트 수신 API 입니다.")
public class NotionWebhookController {

    private static final Logger log = LoggerFactory.getLogger(NotionWebhookController.class);

    private final NotionWebhookService notionWebhookService;

    /**
     * Notion 웹훅 수신 엔드포인트.
     * - 구독 검증: {@code { "verification_token": "..." }} 페이로드 → {@code { "challenge": "..." }} 반환
     * - 일반 이벤트: 이벤트 처리
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(hidden = true,
            summary = "Notion 웹훅 수신",
            description = """
                    Notion 서버로부터 웹훅 요청을 수신합니다.
                    - verification_token 페이로드: 구독 검증 challenge 반환
                    - 일반 이벤트: X-Notion-Signature 헤더 검증 후 처리
                    """
    )
    public ResponseEntity<?> handleWebhook(
            @RequestBody String rawBody
    ) {
        java.util.Optional<String> challenge = notionWebhookService.handle(rawBody);
        if (challenge.isPresent()) {
            log.info("WEBHOOK_RESPONSE: challenge={}", challenge.get());
            return ResponseEntity.ok(Map.of("challenge", challenge.get()));
        }
        log.info("WEBHOOK_RESPONSE: ok");
        return ResponseEntity.ok(SuccessResponse.ok(null));
    }
}
