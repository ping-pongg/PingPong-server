package pingpong.backend.global.auth.service;

import pingpong.backend.global.auth.dto.LoginResponse;

public interface AuthService {
    LoginResponse loginDtoByEmail(String email);
    String reissueAccessToken(String refreshToken);
}