package pingpong.backend.global.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnExpression("!'${discord.webhook-url:}'.isEmpty()")
public class DiscordNotificationService {

    private final RestClient restClient;
    private final String webhookUrl;

    public DiscordNotificationService(@Value("${discord.webhook-url}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.restClient = RestClient.create();
    }

    @Async
    public void sendErrorNotification(Exception e, String requestUrl) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String errorClass = e.getClass().getSimpleName();
            String errorMessage = e.getMessage() != null ? e.getMessage() : "No message";

            if (errorMessage.length() > 1000) {
                errorMessage = errorMessage.substring(0, 1000) + "...";
            }

            Map<String, Object> embed = Map.of(
                    "title", "🚨 서버 에러 발생",
                    "color", 16711680,
                    "fields", List.of(
                            Map.of("name", "에러 클래스", "value", "`" + errorClass + "`", "inline", true),
                            Map.of("name", "요청 URL", "value", "`" + requestUrl + "`", "inline", true),
                            Map.of("name", "발생 시각", "value", timestamp, "inline", false),
                            Map.of("name", "에러 메시지", "value", "```\n" + errorMessage + "\n```", "inline", false)
                    )
            );

            Map<String, Object> payload = Map.of("embeds", List.of(embed));

            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Discord 알림 전송 실패: {}", ex.getMessage());
        }
    }
}
