package pingpong.backend.global.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pingpong.backend.domain.member.Member;
import pingpong.backend.domain.member.service.MemberService;
import pingpong.backend.global.auth.dto.CustomUserDetails;
import java.io.IOException;

@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final MemberService memberService;

    public JwtFilter(JwtUtil jwtUtil, MemberService memberService) {
        this.jwtUtil = jwtUtil;
        this.memberService = memberService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        log.debug("JWT filter: dispatcherType={}, method={}, uri={}, asyncStarted={}",
                request.getDispatcherType(), request.getMethod(), request.getRequestURI(), request.isAsyncStarted());

        // request 에서 Authorization 헤더 획득
        String authorization = request.getHeader("Authorization");
        log.debug("Auth header present? {}", authorization != null);

        // Authorization 헤더 검증
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // "Bearer " 이후 토큰만 추출
        String token = authorization.substring(7);

        // 토큰 유효성 검증
        if (jwtUtil.isExpired(token)) {
            log.debug("JWT token expired");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 토큰에서 email 획득
        String email = jwtUtil.getEmail(token);
        log.debug("Extracted email from JWT: {}", email);

        Member member = memberService.findByEmail(email);

        // UserDetails에 회원 정보 객체 담기
        CustomUserDetails customUserDetails = new CustomUserDetails(member);

        // 스프링 시큐리티 인증 토큰 생성
        Authentication authToken =
                new UsernamePasswordAuthenticationToken(
                        customUserDetails,
                        null,
                        customUserDetails.getAuthorities()
                );

        // SecurityContext 에 인증 정보 저장
        SecurityContextHolder.getContext().setAuthentication(authToken);

        filterChain.doFilter(request, response);
    }
}
