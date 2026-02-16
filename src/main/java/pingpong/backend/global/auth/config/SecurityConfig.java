package pingpong.backend.global.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pingpong.backend.domain.member.service.MemberService;
import pingpong.backend.global.auth.jwt.JwtFilter;
import pingpong.backend.global.auth.jwt.JwtUtil;
import pingpong.backend.global.auth.jwt.LoginFilter;
import pingpong.backend.global.auth.service.AuthService;
import pingpong.backend.global.redis.RefreshTokenCacheUtil;

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

            "/api/v1/s3/get-url"
    };

    private static final String[] ALLOWED_POST_URLS = {
            "/api/v1/auth/login",
            "/api/v1/auth/reissue",
            "/api/v1/members",
            "/api/v1/s3/post-url"
    };



    private final RefreshTokenCacheUtil refreshTokenCacheUtil;
    private final JwtUtil jwtUtil;
    private final AuthService authService;

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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.GET, ALLOWED_GET_URLS).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, ALLOWED_POST_URLS).permitAll()
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
}