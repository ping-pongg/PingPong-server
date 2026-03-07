package pingpong.backend.global.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import pingpong.backend.domain.member.service.MemberMcpConnectionService;
import pingpong.backend.global.auth.jwt.JwtUtil;

import java.io.IOException;

@RequiredArgsConstructor
public class McpLastUsedFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final MemberMcpConnectionService mcpConnectionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            try {
                if (!jwtUtil.isExpired(token) && jwtUtil.isMcpToken(token)) {
                    String email = jwtUtil.getEmail(token);
                    mcpConnectionService.updateLastUsedAt(email);
                }
            } catch (Exception ignored) {
            }
        }

        filterChain.doFilter(request, response);
    }
}
