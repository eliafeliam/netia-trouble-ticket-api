package com.netia.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netia.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the Trouble Ticket API.
 *
 * WHY STATELESS session management:
 *   The API is stateless by design — every request carries a self-contained JWT.
 *   STATELESS tells Spring Security never to create an HttpSession, which:
 *     1. Eliminates session memory overhead on the server.
 *     2. Allows any replica to handle any request (no sticky sessions needed).
 *     3. Removes the CSRF attack vector that requires sessions to exploit.
 *
 * WHY CSRF disabled:
 *   CSRF attacks exploit the browser's automatic cookie submission. Our API uses
 *   Bearer tokens in the Authorization header — browsers never send these automatically,
 *   so CSRF is not applicable. Disabling it removes unnecessary overhead.
 *
 * WHY /actuator/** is public (not authenticated):
 *   Kubernetes liveness and readiness probes are sent by the kubelet without credentials.
 *   If /actuator/health were protected, all probes would return 401, causing Kubernetes
 *   to perpetually restart healthy pods. In production, network-level access controls
 *   (Kubernetes NetworkPolicy) restrict actuator endpoints to the cluster internal network.
 *
 * PRODUCTION NOTE — migrate to OAuth2 Resource Server:
 *   Replace this JWT filter with Spring Security's built-in OAuth2 Resource Server
 *   support (spring-security-oauth2-resource-server). That handles JWKS key rotation,
 *   clock skew, and issuer validation automatically:
 *
 *     http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
 *
 *   Combined with spring.security.oauth2.resourceserver.jwt.issuer-uri pointing to
 *   Keycloak, you get production-grade token validation with zero custom code.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs",     "/v3/api-docs/**",
                                "/actuator/**",
                                // /auth/** — dev-only token endpoint (AuthController is @Profile("!prod")).
                                // In prod this path returns 404 because the controller bean does not exist.
                                // Keeping it in permitAll is harmless and avoids profile-conditional security config.
                                "/auth/**",
                                "/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse body = ErrorResponse.builder()
                    .code("UNAUTHORIZED")
                    .message("Brak poprawnego Bearer tokenu.")
                    .build();
            objectMapper.writeValue(response.getOutputStream(), body);
        };
    }
}
