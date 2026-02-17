package pingpong.backend.domain.chat.stream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 채팅 스트림 메타데이터
 * Redis에 저장되는 스트림 정보
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamMetadata implements Serializable {

    private String streamId;

    private Long teamId;

    private Long memberId;

    private StreamStatus status;

    private String message;

    private Long createdAt;
}
