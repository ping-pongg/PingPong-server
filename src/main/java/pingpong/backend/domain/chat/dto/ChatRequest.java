package pingpong.backend.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "채팅 요청")
public record ChatRequest(
        @NotBlank @Schema(description = "사용자 메시지", example = "이번 스프린트 목표가 뭐야?")
        String message
) {}
