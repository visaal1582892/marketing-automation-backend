package com.medplus.marketing_automation_backend.config;

import com.medplus.marketing_automation_backend.service.CustomUserDetailsService;
import com.medplus.marketing_automation_backend.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final CustomUserDetailsService userDetailsService;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-origin-patterns:}")
    private String allowedOriginPatterns;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          CustomUserDetailsService userDetailsService) {
        this.jwtFilter = jwtFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new org.springframework.security.authentication.ProviderManager(provider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // NOTE:
        // - allowed-origins: exact origin matches (e.g. https://app.example.com)
        // - allowed-origin-patterns: wildcard patterns (e.g. https://*.ngrok-free.app)
        // When allowCredentials=true and "*" is desired, it must be configured via
        // allowed-origin-patterns (never via allowed-origins).
        List<String> origins = splitCsv(allowedOrigins);
        List<String> originPatterns = splitCsv(allowedOriginPatterns);

        if (origins.contains("*")) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else if (!originPatterns.isEmpty()) {
            config.setAllowedOriginPatterns(originPatterns);
            if (!origins.isEmpty()) {
                config.setAllowedOrigins(origins);
            }
        } else {
            config.setAllowedOrigins(origins);
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // Explicitly wire the bean so the empty-lambda default is never used
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // OPTIONS preflight must be allowed before JWT filter runs
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/error", "/actuator/health").permitAll()
                        // WebSocket handshake — auth is handled at STOMP level by JwtChannelInterceptor
                        .requestMatchers("/ws/**").permitAll()
                        // Master data reads are open to any authenticated user (form dropdowns)
                        .requestMatchers(org.springframework.http.HttpMethod.GET,  "/api/master/**").authenticated()
                        // Master data writes — Admin and Marketing Manager can manage lookup tables
                        .requestMatchers(org.springframework.http.HttpMethod.POST,   "/api/master/**").hasAnyRole("ADMIN", "MARKETING_MANAGER")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT,    "/api/master/**").hasAnyRole("ADMIN", "MARKETING_MANAGER")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/master/**").hasAnyRole("ADMIN", "MARKETING_MANAGER")
                        // User management and Question Library are open to Admin and Marketing Manager
                        .requestMatchers("/api/admin/users", "/api/admin/users/**").hasAnyRole("ADMIN", "MARKETING_MANAGER")
                        .requestMatchers("/api/admin/questions", "/api/admin/questions/**").hasAnyRole("ADMIN", "MARKETING_MANAGER")
                        // Everything else under /api/admin/** is Admin-only
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/manager/**").hasAnyRole("ADMIN", "MARKETING_MANAGER", "PROCUREMENT_MANAGER")
                        .requestMatchers("/api/campaigns/**", "/api/tasks/**",
                                         "/api/collaborations/**", "/api/enums/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\""
                                    + ex.getMessage() + "\"}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"Forbidden\",\"message\":\""
                                    + ex.getMessage() + "\"}");
                        }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
