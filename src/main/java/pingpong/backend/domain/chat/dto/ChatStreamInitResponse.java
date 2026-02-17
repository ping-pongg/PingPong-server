package pingpong.backend.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채팅 스트리밍 초기화 응답")
public record ChatStreamInitResponse(
        @Schema(description = "스트림 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        String streamId
) {}
