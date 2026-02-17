package pingpong.backend.domain.chat.stream;

/**
 * 채팅 스트림의 상태를 나타내는 Enum
 */
public enum StreamStatus {
    PENDING,    // Stream 생성 후 시작 되지 않음
    STREAMING,  // Stream 진행 중
    COMPLETED,  // Stream 정상 완료
    ERROR       // Stream 도중 오류 발생
}
