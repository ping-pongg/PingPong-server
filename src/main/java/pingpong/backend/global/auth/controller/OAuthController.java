package pingpong.backend.global.auth.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.member.repository.MemberRepository;
import pingpong.backend.domain.team.MemberTeam;
import pingpong.backend.domain.team.Team;
import pingpong.backend.domain.team.repository.MemberTeamRepository;
import pingpong.backend.domain.team.repository.TeamRepository;
import pingpong.backend.global.auth.dto.OAuthTokenResponse;
import pingpong.backend.global.auth.service.OAuthAuthorizationService;
import pingpong.backend.global.exception.CustomException;
import pingpong.backend.global.redis.OAuthClientCacheUtil;
import pingpong.backend.global.redis.OAuthStepCacheUtil;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Hidden
@RestController
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthAuthorizationService oAuthAuthorizationService;
    private final TeamRepository teamRepository;
    private final OAuthClientCacheUtil oAuthClientCacheUtil;
    private final OAuthStepCacheUtil oAuthStepCacheUtil;
    private final MemberTeamRepository memberTeamRepository;
    private final MemberRepository memberRepository;

    @Value("${PUBLIC_BASE_URL:https://pingpongg.site}")
    private String publicBaseUrl;

    @GetMapping("/.well-known/oauth-authorization-server")
    public ResponseEntity<Map<String, Object>> getAuthServerMetadata() {
        Map<String, Object> metadata = Map.of(
                "issuer", publicBaseUrl,
                "authorization_endpoint", publicBaseUrl + "/oauth/authorize",
                "token_endpoint", publicBaseUrl + "/oauth/token",
                "registration_endpoint", publicBaseUrl + "/oauth/register",
                "response_types_supported", List.of("code"),
                "grant_types_supported", List.of("authorization_code", "refresh_token"),
                "code_challenge_methods_supported", List.of("S256")
        );
        return ResponseEntity.ok(metadata);
    }

    @PostMapping("/oauth/register")
    public ResponseEntity<Map<String, Object>> registerClient(@RequestBody Map<String, Object> body) {
        Object rawUris = body.get("redirect_uris");
        if (!(rawUris instanceof List<?> uriList) || uriList.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid_client_metadata",
                            "error_description", "redirect_uris is required and must not be empty"));
        }
        List<String> redirectUris = uriList.stream()
                .filter(u -> u instanceof String)
                .map(u -> (String) u)
                .toList();

        String clientId = UUID.randomUUID().toString();
        oAuthClientCacheUtil.saveClient(clientId, redirectUris);

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("client_id", clientId);
        if (body.containsKey("client_name")) response.put("client_name", body.get("client_name"));
        response.put("redirect_uris", redirectUris);
        response.put("grant_types", body.getOrDefault("grant_types", List.of("authorization_code", "refresh_token")));
        response.put("response_types", body.getOrDefault("response_types", List.of("code")));
        response.put("token_endpoint_auth_method", "none");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/oauth/authorize")
    public ResponseEntity<String> showAuthorizeForm(
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error
    ) {
        if (redirectUri == null || codeChallenge == null || clientId == null) {
            String errorHtml = """
                    <!DOCTYPE html>
                    <html lang="ko">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>Nexus MCP</title>
                      <style>
                        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
                        body {
                          min-height: 100vh; display: flex; align-items: center; justify-content: center;
                          background: #FDF6F0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                          padding: 24px;
                        }
                        .card {
                          background: #fff; border-radius: 20px;
                          box-shadow: 0 4px 6px -1px rgba(217,119,87,0.08), 0 12px 32px -4px rgba(217,119,87,0.12);
                          padding: 40px 36px; width: 100%%; max-width: 420px; text-align: center;
                        }
                        .icon { font-size: 40px; margin-bottom: 16px; }
                        .title { font-size: 20px; font-weight: 700; color: #1a1a1a; margin-bottom: 10px; }
                        .desc { font-size: 14px; color: #888; line-height: 1.6; }
                      </style>
                    </head>
                    <body>
                      <div class="card">
                        <div class="icon">🚫</div>
                        <div class="title">직접 접근할 수 없습니다</div>
                        <div class="desc">이 페이지는 MCP 클라이언트(Claude Desktop 등)를 통해서만 접근할 수 있습니다.<br>MCP 설정을 통해 연결을 시작해주세요.</div>
                      </div>
                    </body>
                    </html>
                    """;
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML).body(errorHtml);
        }

        List<String> registeredUris = oAuthClientCacheUtil.getRedirectUris(clientId)
                .orElse(null);
        if (registeredUris == null) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML)
                    .body(buildRedirectUriErrorPage("client_id를 찾을 수 없습니다. MCP 클라이언트를 재등록해주세요."));
        }
        String redirectUriValidationError = validateRedirectUri(redirectUri, registeredUris);
        if (redirectUriValidationError != null) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML)
                    .body(buildRedirectUriErrorPage(redirectUriValidationError));
        }

        String errorBlock = (error != null)
                ? "<div class=\"error\"><svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"/><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"/></svg>이메일 또는 비밀번호를 확인해주세요.</div>"
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
                      <input type="hidden" name="client_id" value="%s">
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

                      <button type="submit">다음</button>
                    </form>

                    <div class="footer">Nexus 계정 정보를 사용합니다</div>
                  </div>
                </body>
                </html>
                """.formatted(
                errorBlock,
                escapeHtml(clientId),
                escapeHtml(redirectUri),
                escapeHtml(codeChallenge),
                state != null ? escapeHtml(state) : ""
        );

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @PostMapping("/oauth/authorize")
    public ResponseEntity<String> processAuthorize(
            @RequestParam("client_id") String clientId,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam(value = "state", required = false) String state
    ) {
        List<String> registeredUris = oAuthClientCacheUtil.getRedirectUris(clientId)
                .orElse(null);
        if (registeredUris == null) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML)
                    .body(buildRedirectUriErrorPage("client_id를 찾을 수 없습니다. MCP 클라이언트를 재등록해주세요."));
        }
        String redirectUriValidationError = validateRedirectUri(redirectUri, registeredUris);
        if (redirectUriValidationError != null) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML)
                    .body(buildRedirectUriErrorPage(redirectUriValidationError));
        }

        if (!oAuthAuthorizationService.verifyCredentials(email, password)) {
            String location = "/oauth/authorize?client_id=" + encode(clientId)
                    + "&redirect_uri=" + encode(redirectUri)
                    + "&code_challenge=" + encode(codeChallenge)
                    + (state != null ? "&state=" + encode(state) : "")
                    + "&error=invalid_credentials";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(location))
                    .build();
        }

        Member member = memberRepository.getByEmail(email);
        List<Long> teamIds = memberTeamRepository.findAllByMemberId(member.getId())
                .stream().map(MemberTeam::getTeamId).toList();
        List<Team> memberTeams = teamRepository.findAllById(teamIds);
        if (memberTeams.isEmpty()) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML)
                    .body(buildGenericErrorPage("소속된 팀이 없습니다",
                            "MCP를 연결하려면 팀에 속해 있어야 합니다. 팀 가입 후 다시 시도해주세요."));
        }

        String stepToken = UUID.randomUUID().toString();
        oAuthStepCacheUtil.saveStep(stepToken, email);

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(buildTeamSelectPage(memberTeams, stepToken, clientId, redirectUri, codeChallenge, state));
    }

    @PostMapping("/oauth/authorize/confirm")
    public ResponseEntity<String> confirmAuthorize(
            @RequestParam("step_token") String stepToken,
            @RequestParam("teamId") Long teamId,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam(value = "state", required = false) String state
    ) {
        List<String> registeredUris = oAuthClientCacheUtil.getRedirectUris(clientId)
                .orElse(null);
        if (registeredUris == null) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML)
                    .body(buildRedirectUriErrorPage("client_id를 찾을 수 없습니다. MCP 클라이언트를 재등록해주세요."));
        }
        String redirectUriValidationError = validateRedirectUri(redirectUri, registeredUris);
        if (redirectUriValidationError != null) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML)
                    .body(buildRedirectUriErrorPage(redirectUriValidationError));
        }

        String email = oAuthStepCacheUtil.getEmail(stepToken).orElse(null);
        if (email == null) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML)
                    .body(buildGenericErrorPage("인증이 만료되었습니다",
                            "처음부터 다시 시도해주세요. MCP 클라이언트를 통해 연결을 재시작하세요."));
        }

        Member member = memberRepository.getByEmail(email);
        if (!memberTeamRepository.existsByTeamIdAndMemberId(teamId, member.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_HTML)
                    .body(buildGenericErrorPage("팀 접근 권한 없음", "해당 팀의 멤버가 아닙니다."));
        }

        oAuthStepCacheUtil.deleteStep(stepToken);

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
    public ResponseEntity<?> token(
            @RequestParam(value = "grant_type") String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier,
            @RequestParam(value = "refresh_token", required = false) String refreshToken
    ) {
        try {
            if ("authorization_code".equals(grantType)) {
                return ResponseEntity.ok(oAuthAuthorizationService.exchangeCode(code, codeVerifier));
            } else if ("refresh_token".equals(grantType)) {
                return ResponseEntity.ok(oAuthAuthorizationService.refreshAccessToken(refreshToken));
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "unsupported_grant_type", "error_description", "Unsupported grant type: " + grantType));
        } catch (CustomException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid_grant", "error_description", e.getErrorCode().getMessage()));
        }
    }

    private String buildTeamSelectPage(List<Team> teams, String stepToken, String clientId,
                                       String redirectUri, String codeChallenge, String state) {
        StringBuilder teamOptions = new StringBuilder();
        for (Team team : teams) {
            teamOptions.append("<option value=\"").append(team.getId()).append("\">")
                    .append(escapeHtml(team.getName())).append("</option>");
        }
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Nexus MCP</title>
                  <style>
                    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                      min-height: 100vh; display: flex; align-items: center; justify-content: center;
                      background: #FDF6F0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                      padding: 24px;
                    }
                    .card {
                      background: #ffffff; border-radius: 20px;
                      box-shadow: 0 4px 6px -1px rgba(217,119,87,0.08), 0 12px 32px -4px rgba(217,119,87,0.12);
                      padding: 40px 36px 36px; width: 100%%; max-width: 420px;
                    }
                    .header { text-align: center; margin-bottom: 32px; }
                    .logo {
                      display: inline-flex; align-items: center; justify-content: center;
                      width: 52px; height: 52px;
                      background: linear-gradient(135deg, #D97757, #C46540);
                      border-radius: 14px; margin-bottom: 16px;
                      box-shadow: 0 4px 12px rgba(217,119,87,0.35);
                    }
                    .logo svg { display: block; }
                    .title { font-size: 22px; font-weight: 700; color: #1a1a1a; letter-spacing: -0.3px; margin-bottom: 6px; }
                    .subtitle { font-size: 13.5px; color: #888; }
                    .divider { height: 1px; background: #F0EAE6; margin-bottom: 28px; }
                    .field { margin-bottom: 18px; }
                    label { display: block; font-size: 13px; font-weight: 600; color: #444; margin-bottom: 6px; letter-spacing: 0.1px; }
                    select {
                      width: 100%%; padding: 11px 14px; font-size: 14.5px; color: #1a1a1a;
                      background: #FAFAFA; border: 1.5px solid #E8E0DB; border-radius: 10px;
                      outline: none; transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
                      appearance: none; -webkit-appearance: none;
                    }
                    select:focus { border-color: #D97757; background: #fff; box-shadow: 0 0 0 3px rgba(217,119,87,0.15); }
                    .select-wrap { position: relative; }
                    .select-wrap::after {
                      content: ''; position: absolute; right: 14px; top: 50%%;
                      transform: translateY(-50%%); width: 0; height: 0;
                      border-left: 5px solid transparent; border-right: 5px solid transparent;
                      border-top: 5px solid #999; pointer-events: none;
                    }
                    button {
                      width: 100%%; padding: 13px; margin-top: 8px; font-size: 15px; font-weight: 600;
                      color: #fff; background: linear-gradient(135deg, #D97757, #C46540);
                      border: none; border-radius: 12px; cursor: pointer; letter-spacing: 0.1px;
                      box-shadow: 0 4px 14px rgba(217,119,87,0.4);
                      transition: opacity 0.15s, transform 0.1s, box-shadow 0.15s;
                    }
                    button:hover { opacity: 0.92; box-shadow: 0 6px 18px rgba(217,119,87,0.45); }
                    button:active { transform: scale(0.985); box-shadow: 0 2px 8px rgba(217,119,87,0.3); }
                    .footer { text-align: center; margin-top: 22px; font-size: 12.5px; color: #aaa; }
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
                      <div class="title">팀 선택</div>
                      <div class="subtitle">연결할 팀을 선택해주세요</div>
                    </div>
                    <div class="divider"></div>
                    <form method="POST" action="/oauth/authorize/confirm">
                      <input type="hidden" name="step_token" value="%s">
                      <input type="hidden" name="client_id" value="%s">
                      <input type="hidden" name="redirect_uri" value="%s">
                      <input type="hidden" name="code_challenge" value="%s">
                      <input type="hidden" name="state" value="%s">
                      <div class="field">
                        <label for="teamId">팀</label>
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
                escapeHtml(stepToken),
                escapeHtml(clientId),
                escapeHtml(redirectUri),
                escapeHtml(codeChallenge),
                state != null ? escapeHtml(state) : "",
                teamOptions.toString()
        );
    }

    private String buildGenericErrorPage(String title, String desc) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Nexus MCP</title>
                  <style>
                    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                      min-height: 100vh; display: flex; align-items: center; justify-content: center;
                      background: #FDF6F0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                      padding: 24px;
                    }
                    .card {
                      background: #fff; border-radius: 20px;
                      box-shadow: 0 4px 6px -1px rgba(217,119,87,0.08), 0 12px 32px -4px rgba(217,119,87,0.12);
                      padding: 40px 36px; width: 100%%; max-width: 420px; text-align: center;
                    }
                    .icon { font-size: 40px; margin-bottom: 16px; }
                    .title { font-size: 20px; font-weight: 700; color: #1a1a1a; margin-bottom: 10px; }
                    .desc { font-size: 14px; color: #888; line-height: 1.6; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="icon">🚫</div>
                    <div class="title">%s</div>
                    <div class="desc">%s</div>
                  </div>
                </body>
                </html>
                """.formatted(escapeHtml(title), escapeHtml(desc));
    }

    private String validateRedirectUri(String redirectUri, List<String> registeredUris) {
        String incomingSchemeHost = extractSchemeHost(redirectUri);
        if (incomingSchemeHost == null) {
            return "redirect_uri 형식이 올바르지 않습니다: " + redirectUri;
        }
        boolean matched = registeredUris.stream()
                .map(this::extractSchemeHost)
                .filter(sh -> sh != null)
                .anyMatch(incomingSchemeHost::equals);
        if (!matched) {
            return "등록되지 않은 redirect_uri입니다: " + redirectUri;
        }
        return null;
    }

    private String extractSchemeHost(String rawUri) {
        try {
            URI uri = new URI(rawUri);
            if (uri.getScheme() == null || uri.getHost() == null) return null;
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private String buildRedirectUriErrorPage(String reason) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Nexus MCP</title>
                  <style>
                    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                      min-height: 100vh; display: flex; align-items: center; justify-content: center;
                      background: #FDF6F0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                      padding: 24px;
                    }
                    .card {
                      background: #fff; border-radius: 20px;
                      box-shadow: 0 4px 6px -1px rgba(217,119,87,0.08), 0 12px 32px -4px rgba(217,119,87,0.12);
                      padding: 40px 36px; width: 100%%; max-width: 480px; text-align: center;
                    }
                    .icon { font-size: 40px; margin-bottom: 16px; }
                    .title { font-size: 20px; font-weight: 700; color: #1a1a1a; margin-bottom: 12px; }
                    .desc { font-size: 14px; color: #888; line-height: 1.6; margin-bottom: 16px; }
                    .reason {
                      font-size: 13px; color: #C04F2A; background: #FFF0EC;
                      border: 1px solid #FDDDD4; border-radius: 10px;
                      padding: 10px 14px; word-break: break-all;
                    }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="icon">🚫</div>
                    <div class="title">잘못된 redirect_uri</div>
                    <div class="desc">OAuth 인가 요청이 거부되었습니다.<br>MCP 클라이언트 설정을 확인하거나 재등록해주세요.</div>
                    <div class="reason">%s</div>
                  </div>
                </body>
                </html>
                """.formatted(escapeHtml(reason));
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
