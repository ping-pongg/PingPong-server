package pingpong.backend.global.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OAuthCodeCacheUtil {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CODE_PREFIX = "oauth:code:";
    private static final Duration CODE_TTL = Duration.ofMinutes(10);

    public record OAuthCodeData(String email, Long teamId, String codeChallenge) {}

    public void saveCode(String code, String email, Long teamId, String codeChallenge) {
        try {
            String value = objectMapper.writeValueAsString(new OAuthCodeData(email, teamId, codeChallenge));
            redisTemplate.opsForValue().set(CODE_PREFIX + code, value, CODE_TTL);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OAuth code data", e);
        }
    }

    public Optional<OAuthCodeData> getCode(String code) {
        String value = redisTemplate.opsForValue().get(CODE_PREFIX + code);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(value, OAuthCodeData.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public void deleteCode(String code) {
        redisTemplate.delete(CODE_PREFIX + code);
    }
}
