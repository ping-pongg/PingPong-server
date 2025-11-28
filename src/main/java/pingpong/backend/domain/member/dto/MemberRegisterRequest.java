package pingpong.backend.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record MemberRegisterRequest(
        @Schema(description = "이메일", example = "example@gmail.com")
        @Email @NotNull String email,

        @Schema(description = "비밀번호", example = "password123")
        @NotNull String password,

        @Schema(description = "닉네임", example = "민지")
        String nickname
) {}

