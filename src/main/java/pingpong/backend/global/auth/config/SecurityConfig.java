package pingpong.backend.global.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;
import pingpong.backend.domain.member.service.MemberService;
import pingpong.backend.global.auth.jwt.JwtFilter;
import pingpong.backend.global.auth.jwt.JwtUtil;
import pingpong.backend.global.auth.jwt.LoginFilter;
import pingpong.backend.global.auth.service.AuthService;
import pingpong.backend.global.redis.RefreshTokenCacheUtil;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] ALLOWED_GET_URLS = {
            // Swagger
            "/swagger-ui/**",
            "/v3/api-docs/**",

            // Member
            "/api/v1/members/*",

            "/api/v1/s3/get-url",

            // Dashboard (HTML/JS/CSS is public; API data is protected by Basic Auth on /internal/**)
            "/dashboard/**"
    };

    private static final String[] ALLOWED_POST_URLS = {
            "/api/v1/auth/login",
            "/api/v1/auth/reissue",
            "/api/v1/members",
            "/api/v1/s3/post-url",
            "/api/v1/notion/webhooks"
    };



    private final RefreshTokenCacheUtil refreshTokenCacheUtil;
    private final JwtUtil jwtUtil;
    private final AuthService authService;

    /**
     * /internal/** 전용 FilterChain (Order=1, 가장 먼저 평가).
     * HTTP Basic Auth로 보호. JWT FilterChain과 완전히 분리.
     * UserDetailsService를 체인 내부(.userDetailsService())에서만 설정하여
     * 전역 customUserDetailsService 빈과 충돌 방지.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(
            HttpSecurity http,
            PasswordEncoder encoder,
            @Value("${internal.dashboard.username}") String username,
            @Value("${internal.dashboard.password}") String password
    ) throws Exception {
        InMemoryUserDetailsManager internalUsers = new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(encoder.encode(password))
                        .roles("INTERNAL")
                        .build()
        );

        // DaoAuthenticationProvider를 직접 구성해 BCryptPasswordEncoder를 명시적으로 지정.
        // .userDetailsService()만 쓰면 DelegatingPasswordEncoder가 기본 적용되어
        // {bcrypt} 접두사 없는 BCrypt 해시를 인식하지 못해 인증 실패함.
        DaoAuthenticationProvider internalAuthProvider = new DaoAuthenticationProvider();
        internalAuthProvider.setUserDetailsService(internalUsers);
        internalAuthProvider.setPasswordEncoder(encoder);

        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(internalAuthProvider)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("INTERNAL"))
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /**
     * 비밀번호 인코더 Bean
     */
    @Bean
    public PasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            UserDetailsService customUserDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(false);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    public JwtFilter jwtFilter(MemberService memberService) {
        return new JwtFilter(jwtUtil, memberService);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           DaoAuthenticationProvider provider,
                                           AuthenticationManager authManager,
                                           JwtFilter jwtFilter) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(provider)
                .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/error", "/error/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, ALLOWED_GET_URLS).permitAll()
                        .requestMatchers(HttpMethod.POST, ALLOWED_POST_URLS).permitAll()
                        .anyRequest().authenticated()
                );

        LoginFilter loginFilter = new LoginFilter(authManager, refreshTokenCacheUtil, jwtUtil, authService);
        loginFilter.setFilterProcessesUrl("/api/v1/auth/login");

        // JWTFilter 등록
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        // LoginFilter 등록
        http.addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            log.error("ACCESS DENIED: {} {} | dispatcher={}",
                    request.getMethod(), request.getRequestURI(), request.getDispatcherType());
            if (response.isCommitted()) {
                return;
            }
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"FORBIDDEN\"}");
        };
    }
}
