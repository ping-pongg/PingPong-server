package pingpong.backend.global.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "이메일", example = "example@gmail.com") @NotBlank String email,
        @Schema(description = "비밀번호", example = "password123") @NotBlank String password
) {}
