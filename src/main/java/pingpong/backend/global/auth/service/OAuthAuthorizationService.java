package pingpong.backend.global.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.member.repository.MemberRepository;
import pingpong.backend.domain.member.service.MemberMcpConnectionService;
import pingpong.backend.global.auth.AuthErrorCode;
import pingpong.backend.global.auth.dto.OAuthTokenResponse;
import pingpong.backend.global.auth.jwt.JwtUtil;
import pingpong.backend.global.exception.CustomException;
import pingpong.backend.global.redis.OAuthCodeCacheUtil;
import pingpong.backend.global.redis.OAuthCodeCacheUtil.OAuthCodeData;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthAuthorizationService {

    private final OAuthCodeCacheUtil oAuthCodeCacheUtil;
    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;
    private final MemberMcpConnectionService mcpConnectionService;
    private final PasswordEncoder passwordEncoder;

    public String generateCode(String email, Long teamId, String codeChallenge) {
        String code = UUID.randomUUID().toString().replace("-", "");
        oAuthCodeCacheUtil.saveCode(code, email, teamId, codeChallenge);
        return code;
    }

    @Transactional
    public OAuthTokenResponse exchangeCode(String code, String codeVerifier) {
        OAuthCodeData data = oAuthCodeCacheUtil.getCode(code)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_OAUTH_CODE));

        verifyPkce(data.codeChallenge(), codeVerifier);

        oAuthCodeCacheUtil.deleteCode(code);

        Member member = memberRepository.getByEmail(data.email());
        mcpConnectionService.upsertConnection(member.getId(), data.teamId());

        String accessToken = jwtUtil.createMcpAccessToken(data.email(), data.teamId());
        String refreshToken = jwtUtil.createMcpRefreshToken(data.email(), data.teamId());

        return OAuthTokenResponse.of(accessToken, refreshToken);
    }

    @Transactional
    public OAuthTokenResponse refreshAccessToken(String refreshToken) {
        if (jwtUtil.isExpired(refreshToken)) {
            throw new CustomException(AuthErrorCode.INVALID_OAUTH_CODE);
        }

        String email = jwtUtil.getEmail(refreshToken);
        Long teamId = jwtUtil.getTeamId(refreshToken);

        String newAccessToken = jwtUtil.createMcpAccessToken(email, teamId);
        String newRefreshToken = jwtUtil.createMcpRefreshToken(email, teamId);

        return OAuthTokenResponse.of(newAccessToken, newRefreshToken);
    }

    public boolean verifyCredentials(String email, String rawPassword) {
        return memberRepository.findByEmail(email)
                .map(member -> passwordEncoder.matches(rawPassword, member.getPassword()))
                .orElse(false);
    }

    private void verifyPkce(String codeChallenge, String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            if (!computed.equals(codeChallenge)) {
                throw new CustomException(AuthErrorCode.INVALID_OAUTH_CODE);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(AuthErrorCode.INVALID_OAUTH_CODE);
        }
    }
}
