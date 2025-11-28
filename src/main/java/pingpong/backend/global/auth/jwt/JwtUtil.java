package pingpong.backend.global.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        byte[] byteSecretKey = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(byteSecretKey);
    }

    /** Claim 파싱 공통부 */
    private Claims parseClaims(String token) {
        String raw = stripBearerPrefix(token);
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(raw)
                .getPayload();
    }

    private String stripBearerPrefix(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }
        String t = token.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return t.substring(7).trim();
        }
        return t;
    }

    /** Claims accessor */
    public String getEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    public boolean isExpired(String token) {
        try {
            Date exp = parseClaims(token).getExpiration();
            return exp != null && exp.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /** 토큰 생성 */
    public String createAccessToken(String email) {
        long accessTokenExpireMs = 1000L * 60 * 1000; // 1000분 (FIXME: 임시값)
        return createJwt(email, accessTokenExpireMs);
    }

    public String createRefreshToken(String email) {
        long refreshTokenExpireMs = 1000L * 60 * 60 * 24 * 7; // 7일
        return createJwt(email, refreshTokenExpireMs);
    }

    public String createJwt(String email, long expiredMs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);

        Date now = new Date();
        Date exp = new Date(now.getTime() + expiredMs);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }
}