package pingpong.backend.global.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.member.repository.MemberRepository;
import pingpong.backend.global.auth.AuthErrorCode;
import pingpong.backend.global.auth.dto.LoginResponse;
import pingpong.backend.global.auth.jwt.JwtUtil;
import pingpong.backend.global.exception.CustomException;
import pingpong.backend.global.redis.RefreshTokenCacheUtil;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final MemberRepository memberRepository;
    private final JwtUtil jwtUtil;
    private final RefreshTokenCacheUtil refreshTokenCacheUtil;

    @Override
    public LoginResponse loginDtoByEmail(String email) {
        Member user = memberRepository.getByEmail(email);
        return LoginResponse.of(user);
    }

    @Override
    public String reissueAccessToken(String refreshToken) {
        validateRefreshToken(refreshToken);

        String email = jwtUtil.getEmail(refreshToken);

        validateStoredRefreshToken(email, refreshToken);

        return jwtUtil.createAccessToken(email);
    }

    private void validateRefreshToken(String refreshToken) {
        if (jwtUtil.isExpired(refreshToken)) {
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    private void validateStoredRefreshToken(String email, String refreshToken) {
        String storedRefreshToken = refreshTokenCacheUtil.getRefreshToken(email);
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
    }
}
