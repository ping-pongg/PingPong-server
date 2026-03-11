package pingpong.backend.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OAuthStepCacheUtil {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String STEP_PREFIX = "oauth:step:";
    private static final Duration STEP_TTL = Duration.ofMinutes(10);

    public void saveStep(String stepToken, String email) {
        redisTemplate.opsForValue().set(STEP_PREFIX + stepToken, email, STEP_TTL);
    }

    public Optional<String> getEmail(String stepToken) {
        String value = redisTemplate.opsForValue().get(STEP_PREFIX + stepToken);
        return Optional.ofNullable(value);
    }

    public void deleteStep(String stepToken) {
        redisTemplate.delete(STEP_PREFIX + stepToken);
    }
}
