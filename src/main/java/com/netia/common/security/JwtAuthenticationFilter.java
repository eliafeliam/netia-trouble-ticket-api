package com.netia.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Extracts and validates Bearer JWT from each request, populating the SecurityContext.
 *
 * WHY OncePerRequestFilter:
 *   Guarantees exactly one execution per request even in async dispatch chains
 *   (e.g. error forwarding). A plain Filter could run twice on the same request.
 *
 * WHY we store tenantId both in SecurityContext and as a request attribute:
 *   SecurityContext (TenantAwarePrincipal) — used by service layer and RLS inspector.
 *   request.setAttribute("tenantId") — used by RateLimitFilter, which runs AFTER this
 *   filter and reads tenantId without touching the SecurityContext.
 *   MDC — used by Logback pattern so every log line emitted during this request
 *   automatically carries [tenantId], enabling per-tenant log filtering in Kibana/Loki.
 *
 * WHY we don't return 401 here on invalid token:
 *   Filters that set the response directly bypass Spring MVC error handling — the
 *   GlobalExceptionHandler is never called, so the error format is inconsistent.
 *   Instead we let the request continue unauthenticated; Spring Security's
 *   AuthenticationEntryPoint (configured in SecurityConfiguration) will produce the
 *   correct 401 JSON response via GlobalExceptionHandler.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null && jwtTokenProvider.isTokenValid(token)) {
                Claims claims = jwtTokenProvider.extractClaims(token);
                String userId   = claims.getSubject();
                String tenantId = claims.get("tenantId", String.class);

                List<GrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        // TENANT: authority lets future @PreAuthorize rules check tenant membership.
                        new SimpleGrantedAuthority("TENANT:" + tenantId)
                );

                Authentication auth = new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal(userId, tenantId), token, authorities);

                SecurityContextHolder.getContext().setAuthentication(auth);
                request.setAttribute("tenantId", tenantId);
                MDC.put("tenantId", tenantId);  // cleared by RequestIdFilter in finally block
                log.debug("Authenticated user={} tenant={}", userId, tenantId);
            } else if (token != null) {
                log.warn("Invalid or expired JWT token");
            }
        } catch (JwtException e) {
            // Log and continue — Spring Security's 401 handler takes over for protected endpoints.
            log.warn("JWT processing failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        return (header != null && header.startsWith(BEARER_PREFIX))
                ? header.substring(BEARER_PREFIX.length())
                : null;
    }
}
