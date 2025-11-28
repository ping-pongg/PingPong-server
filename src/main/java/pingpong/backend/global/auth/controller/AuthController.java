package pingpong.backend.global.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pingpong.backend.global.auth.dto.LoginRequest;
import pingpong.backend.global.auth.service.AuthService;

@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "인증 API", description = "로그인, 토큰 재발급 등 인증 관련 API 입니다.")
@RequestMapping("api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인 후 JWT 토큰을 헤더에 담아 반환합니다.")
    public ResponseEntity<Void> login(
            @RequestBody @Valid LoginRequest request
    ) {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reissue")
    @Operation(summary = "Access Token 재발급", description = "Refresh Token를 헤더로 받아 Access Token을 재발급 합니다.")
    public ResponseEntity<Void> reissue(
            @RequestHeader("Refresh-Token") String refreshToken
    ) {
        String newAccessToken = authService.reissueAccessToken(refreshToken);
        return ResponseEntity.ok()
                .header("Authorization", "Bearer " + newAccessToken)
                .build();
    }
}
