package pingpong.backend.global.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.repository.TeamRepository;
import pingpong.backend.global.auth.dto.OAuthTokenResponse;
import pingpong.backend.global.auth.service.OAuthAuthorizationService;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthAuthorizationService oAuthAuthorizationService;
    private final TeamRepository teamRepository;

    @Value("${PUBLIC_BASE_URL:https://pingpong-team.com}")
    private String publicBaseUrl;

    @GetMapping("/.well-known/oauth-authorization-server")
    public ResponseEntity<Map<String, Object>> getAuthServerMetadata() {
        Map<String, Object> metadata = Map.of(
                "issuer", publicBaseUrl,
                "authorization_endpoint", publicBaseUrl + "/oauth/authorize",
                "token_endpoint", publicBaseUrl + "/oauth/token",
                "response_types_supported", List.of("code"),
                "grant_types_supported", List.of("authorization_code", "refresh_token"),
                "code_challenge_methods_supported", List.of("S256")
        );
        return ResponseEntity.ok(metadata);
    }

    @GetMapping("/oauth/authorize")
    public ResponseEntity<String> showAuthorizeForm(
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error
    ) {
        List<Team> teams = teamRepository.findAll();
        StringBuilder teamOptions = new StringBuilder();
        for (Team team : teams) {
            teamOptions.append("<option value=\"").append(team.getId()).append("\">")
                    .append(escapeHtml(team.getName())).append("</option>");
        }

        String errorBlock = (error != null)
                ? "<div class=\"error\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"/><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"/></svg>이메일, 비밀번호 또는 팀을 확인해주세요.</div>"
                : "";

        String html = """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Nexus MCP</title>
                  <style>
                    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

                    body {
                      min-height: 100vh;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      background: #FDF6F0;
                      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                      padding: 24px;
                    }

                    .card {
                      background: #ffffff;
                      border-radius: 20px;
                      box-shadow: 0 4px 6px -1px rgba(217, 119, 87, 0.08),
                                  0 12px 32px -4px rgba(217, 119, 87, 0.12);
                      padding: 40px 36px 36px;
                      width: 100%%;
                      max-width: 420px;
                    }

                    .header {
                      text-align: center;
                      margin-bottom: 32px;
                    }

                    .logo {
                      display: inline-flex;
                      align-items: center;
                      justify-content: center;
                      width: 52px;
                      height: 52px;
                      background: linear-gradient(135deg, #D97757, #C46540);
                      border-radius: 14px;
                      margin-bottom: 16px;
                      box-shadow: 0 4px 12px rgba(217, 119, 87, 0.35);
                    }

                    .logo svg { display: block; }

                    .title {
                      font-size: 22px;
                      font-weight: 700;
                      color: #1a1a1a;
                      letter-spacing: -0.3px;
                      margin-bottom: 6px;
                    }

                    .subtitle {
                      font-size: 13.5px;
                      color: #888;
                    }

                    .divider {
                      height: 1px;
                      background: #F0EAE6;
                      margin-bottom: 28px;
                    }

                    .error {
                      display: flex;
                      align-items: center;
                      gap: 8px;
                      background: #FFF0EC;
                      color: #C04F2A;
                      font-size: 13.5px;
                      font-weight: 500;
                      padding: 10px 14px;
                      border-radius: 10px;
                      border: 1px solid #FDDDD4;
                      margin-bottom: 20px;
                    }

                    .field {
                      margin-bottom: 18px;
                    }

                    label {
                      display: block;
                      font-size: 13px;
                      font-weight: 600;
                      color: #444;
                      margin-bottom: 6px;
                      letter-spacing: 0.1px;
                    }

                    input, select {
                      width: 100%%;
                      padding: 11px 14px;
                      font-size: 14.5px;
                      color: #1a1a1a;
                      background: #FAFAFA;
                      border: 1.5px solid #E8E0DB;
                      border-radius: 10px;
                      outline: none;
                      transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
                      appearance: none;
                      -webkit-appearance: none;
                    }

                    input::placeholder { color: #bbb; }

                    input:focus, select:focus {
                      border-color: #D97757;
                      background: #fff;
                      box-shadow: 0 0 0 3px rgba(217, 119, 87, 0.15);
                    }

                    .select-wrap {
                      position: relative;
                    }

                    .select-wrap::after {
                      content: '';
                      position: absolute;
                      right: 14px;
                      top: 50%%;
                      transform: translateY(-50%%);
                      width: 0;
                      height: 0;
                      border-left: 5px solid transparent;
                      border-right: 5px solid transparent;
                      border-top: 5px solid #999;
                      pointer-events: none;
                    }

                    button {
                      width: 100%%;
                      padding: 13px;
                      margin-top: 8px;
                      font-size: 15px;
                      font-weight: 600;
                      color: #fff;
                      background: linear-gradient(135deg, #D97757, #C46540);
                      border: none;
                      border-radius: 12px;
                      cursor: pointer;
                      letter-spacing: 0.1px;
                      box-shadow: 0 4px 14px rgba(217, 119, 87, 0.4);
                      transition: opacity 0.15s, transform 0.1s, box-shadow 0.15s;
                    }

                    button:hover {
                      opacity: 0.92;
                      box-shadow: 0 6px 18px rgba(217, 119, 87, 0.45);
                    }

                    button:active {
                      transform: scale(0.985);
                      box-shadow: 0 2px 8px rgba(217, 119, 87, 0.3);
                    }

                    .footer {
                      text-align: center;
                      margin-top: 22px;
                      font-size: 12.5px;
                      color: #aaa;
                    }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="header">
                      <div class="logo">
                        <svg width="28" height="28" viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg">
                          <path d="M14 4C8.477 4 4 8.477 4 14s4.477 10 10 10 10-4.477 10-10S19.523 4 14 4z" fill="rgba(255,255,255,0.25)"/>
                          <path d="M9.5 14a4.5 4.5 0 019 0" stroke="white" stroke-width="2" stroke-linecap="round"/>
                          <circle cx="10" cy="11.5" r="1.5" fill="white"/>
                          <circle cx="18" cy="11.5" r="1.5" fill="white"/>
                          <path d="M10 17.5c1.1 1.2 2.5 1.8 4 1.8s2.9-.6 4-1.8" stroke="white" stroke-width="1.8" stroke-linecap="round"/>
                        </svg>
                      </div>
                      <div class="title">Nexus MCP</div>
                      <div class="subtitle">Nexus 서비스의 계정 정보로 MCP 서버에 연결합니다</div>
                    </div>

                    <div class="divider"></div>

                    %s

                    <form method="POST" action="/oauth/authorize">
                      <input type="hidden" name="redirect_uri" value="%s">
                      <input type="hidden" name="code_challenge" value="%s">
                      <input type="hidden" name="state" value="%s">

                      <div class="field">
                        <label for="email">이메일</label>
                        <input id="email" type="email" name="email" required placeholder="이메일을 입력하세요" autocomplete="email">
                      </div>

                      <div class="field">
                        <label for="password">비밀번호</label>
                        <input id="password" type="password" name="password" required placeholder="비밀번호를 입력하세요" autocomplete="current-password">
                      </div>

                      <div class="field">
                        <label for="teamId">팀 선택</label>
                        <div class="select-wrap">
                          <select id="teamId" name="teamId" required>
                            <option value="">연결할 팀을 선택하세요</option>
                            %s
                          </select>
                        </div>
                      </div>

                      <button type="submit">MCP 연결하기</button>
                    </form>

                    <div class="footer">Nexus 계정 정보를 사용합니다</div>
                  </div>
                </body>
                </html>
                """.formatted(
                errorBlock,
                escapeHtml(redirectUri),
                escapeHtml(codeChallenge),
                state != null ? escapeHtml(state) : "",
                teamOptions.toString()
        );

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @PostMapping("/oauth/authorize")
    public ResponseEntity<Void> processAuthorize(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("teamId") Long teamId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam(value = "state", required = false) String state
    ) {
        if (!oAuthAuthorizationService.verifyCredentials(email, password)) {
            String location = "/oauth/authorize?redirect_uri=" + encode(redirectUri)
                    + "&code_challenge=" + encode(codeChallenge)
                    + (state != null ? "&state=" + encode(state) : "")
                    + "&error=invalid_credentials";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(location))
                    .build();
        }

        String code = oAuthAuthorizationService.generateCode(email, teamId, codeChallenge);

        StringBuilder location = new StringBuilder(redirectUri);
        location.append(redirectUri.contains("?") ? "&" : "?").append("code=").append(code);
        if (state != null && !state.isBlank()) {
            location.append("&state=").append(encode(state));
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(location.toString()))
                .build();
    }

    @PostMapping("/oauth/token")
    public ResponseEntity<OAuthTokenResponse> token(
            @RequestParam(value = "grant_type") String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier,
            @RequestParam(value = "refresh_token", required = false) String refreshToken
    ) {
        if ("authorization_code".equals(grantType)) {
            OAuthTokenResponse response = oAuthAuthorizationService.exchangeCode(code, codeVerifier);
            return ResponseEntity.ok(response);
        } else if ("refresh_token".equals(grantType)) {
            OAuthTokenResponse response = oAuthAuthorizationService.refreshAccessToken(refreshToken);
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().build();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
