package pingpong.backend.global.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OAuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {
    public static OAuthTokenResponse of(String accessToken, String refreshToken) {
        return new OAuthTokenResponse(accessToken, refreshToken, "Bearer", 3600);
    }
}
