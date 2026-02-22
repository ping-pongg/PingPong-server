package pingpong.backend.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import pingpong.backend.domain.chat.stream.StreamMetadata;

import java.util.concurrent.Executor;

/**
 * 채팅 스트리밍 설정
 * ThreadPoolTaskExecutor 및 Redis 설정 포함
 */
@Configuration
@EnableAsync
public class ChatStreamConfig {

    /**
     * 채팅 스트리밍 전용 ThreadPoolTaskExecutor
     * 비동기 스트리밍 처리를 위한 스레드 풀
     */
    @Bean(name = "chatStreamExecutor")
    public Executor chatStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 평가 전용 ThreadPoolTaskExecutor.
     * 응답 반환 후 비동기로 Judge 호출 및 DB INSERT 처리.
     */
    @Bean(name = "evalExecutor")
    public Executor evalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("eval-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 스트림 메타데이터 저장을 위한 RedisTemplate
     * Jackson2JsonRedisSerializer를 사용하여 StreamMetadata 객체를 JSON으로 직렬화
     */
    @Bean
    public RedisTemplate<String, StreamMetadata> streamRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper
    ) {
        RedisTemplate<String, StreamMetadata> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Jackson2JsonRedisSerializer로 명시적 타입 지정
        Jackson2JsonRedisSerializer<StreamMetadata> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, StreamMetadata.class);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}
