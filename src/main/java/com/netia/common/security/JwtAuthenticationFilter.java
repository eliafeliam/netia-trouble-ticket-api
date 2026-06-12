package com.netia.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (token != null && jwtTokenProvider.isTokenValid(token)) {
                Claims claims = jwtTokenProvider.extractClaims(token);
                String userId = claims.getSubject();
                String tenantId = claims.get("tenantId", String.class);

                List<GrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("TENANT:" + tenantId)
                );

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal(userId, tenantId),
                        token,
                        authorities
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute("tenantId", tenantId);
                // also put tenantId into MDC for logging; RequestIdFilter will clear MDC at the end
                try {
                    org.slf4j.MDC.put("tenantId", tenantId);
                } catch (Exception ignore) {
                    // ignore if MDC not available
                }
                log.debug("Authentication set for user: {} in tenant: {}", userId, tenantId);
            } else if (token != null) {
                log.warn("Invalid or expired token");
            }
        } catch (JwtException e) {
            log.warn("Could not set user authentication in security context", e);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);

        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}


