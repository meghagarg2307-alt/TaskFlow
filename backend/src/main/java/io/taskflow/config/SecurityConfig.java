package io.taskflow.config;

import io.taskflow.security.JwtAuthenticationEntryPoint;
import io.taskflow.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Stateless API security:
 * <ul>
 *   <li><b>Sessions disabled</b> — JWT carries identity; no JSESSIONID, scales horizontally
 *       (any backend instance can serve any request → fits Docker Compose / ECS / K8s).</li>
 *   <li><b>CSRF disabled</b> for the API. The bearer-header design is immune by construction
 *       (browsers can't set custom headers cross-origin without CORS). The refresh-token
 *       cookie is protected by {@code SameSite=Strict} (set in {@code AuthController}).</li>
 *   <li><b>CORS</b> is permissive only for configured Angular origins; credentials are
 *       enabled so the refresh-token cookie can ride along.</li>
 *   <li><b>Role hierarchy:</b> {@code ADMIN > MANAGER > MEMBER}. {@code @PreAuthorize("hasRole('MEMBER')")}
 *       is satisfied by an admin.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authEntryPoint;
    private final SecurityProperties securityProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(eh -> eh.authenticationEntryPoint(authEntryPoint))
            .authorizeHttpRequests(reg -> reg
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(
                        "/auth/register",
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/logout",        // idempotent; cookie alone is enough
                        "/actuator/health",
                        "/actuator/info",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/ws/**"               // STOMP handshake is authenticated inside the WS layer (Step 4)
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** BCrypt strength 12 → ~250 ms per hash on a modern server. Sweet spot for 2026. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_MANAGER
                ROLE_MANAGER > ROLE_MEMBER
                """);
    }

    /** Wires the role hierarchy into method security ({@code @PreAuthorize}). */
    @Bean
    static DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy rh) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(rh);
        return handler;
    }

    @Bean
    public CorsFilter corsFilter() {
        return new CorsFilter(corsConfigurationSource());
    }

    private UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(securityProperties.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setExposedHeaders(List.of("Location"));
        cors.setAllowCredentials(true);   // refresh-token cookie
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
