package pingpong.backend.domain.chat.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 채팅 스트림 메타데이터 관리를 위한 Redis 유틸리티
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStreamManager {

    private final RedisTemplate<String, StreamMetadata> streamRedisTemplate;

    private static final String STREAM_PREFIX = "chat_stream:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /**
     * 스트림 메타데이터를 Redis에 저장
     *
     * @param metadata 저장할 스트림 메타데이터
     */
    public void saveStream(StreamMetadata metadata) {
        String key = STREAM_PREFIX + metadata.getStreamId();
        streamRedisTemplate.opsForValue().set(key, metadata, DEFAULT_TTL);
        log.debug("Stream saved: streamId={}, teamId={}, memberId={}",
                metadata.getStreamId(), metadata.getTeamId(), metadata.getMemberId());
    }

    /**
     * 스트림 메타데이터를 Redis에서 조회
     *
     * @param streamId 조회할 스트림 ID
     * @return 스트림 메타데이터 (존재하지 않으면 empty)
     */
    public Optional<StreamMetadata> getStream(String streamId) {
        String key = STREAM_PREFIX + streamId;
        StreamMetadata metadata = streamRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(metadata);
    }

    /**
     * 스트림 상태를 업데이트
     *
     * @param streamId 업데이트할 스트림 ID
     * @param status 새로운 상태
     */
    public void updateStatus(String streamId, StreamStatus status) {
        getStream(streamId).ifPresent(metadata -> {
            StreamMetadata updated = StreamMetadata.builder()
                    .streamId(metadata.getStreamId())
                    .teamId(metadata.getTeamId())
                    .memberId(metadata.getMemberId())
                    .status(status)
                    .message(metadata.getMessage())
                    .createdAt(metadata.getCreatedAt())
                    .build();

            String key = STREAM_PREFIX + streamId;
            streamRedisTemplate.opsForValue().set(key, updated, DEFAULT_TTL);
            log.debug("Stream status updated: streamId={}, status={}", streamId, status);
        });
    }

    /**
     * 스트림 메타데이터를 Redis에서 삭제
     *
     * @param streamId 삭제할 스트림 ID
     */
    public void deleteStream(String streamId) {
        String key = STREAM_PREFIX + streamId;
        streamRedisTemplate.delete(key);
        log.debug("Stream deleted: streamId={}", streamId);
    }
}
