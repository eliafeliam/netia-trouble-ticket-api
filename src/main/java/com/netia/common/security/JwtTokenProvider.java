package com.netia.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Generates and validates JWT tokens for local/dev/test environments.
 *
 * WHY HS256 (symmetric) here, not RS256/JWKS:
 *   HS256 uses a single shared secret, which is sufficient for a self-contained service
 *   where this same application both issues and validates tokens (e.g. generate-token.sh).
 *
 * PRODUCTION NOTE — replace with Keycloak / OIDC:
 *   In production, token issuance moves to a centralised Identity Provider (Keycloak, Azure AD).
 *   This class becomes unnecessary: Spring Security's OAuth2 Resource Server validates tokens
 *   using the IdP's public JWKS endpoint (asymmetric RS256). Configuration in application.yml:
 *
 *     spring.security.oauth2.resourceserver.jwt.issuer-uri: https://keycloak.example.com/realms/netia
 *     spring.security.oauth2.resourceserver.jwt.jwk-set-uri: https://.../.../certs
 *
 *   WHY JWKS (asymmetric) is safer for prod:
 *     With HS256 the secret must be known to both issuer and verifier — if the API is
 *     compromised an attacker can forge tokens. With RS256/JWKS only Keycloak holds the
 *     private key; the API only holds the public key (useless for forging).
 *
 * WHY JWT_SECRET has no default value:
 *   If we set a default, the app would boot with a guessable key when the env var is missing.
 *   Failing fast (startup error) is safer than running with a known-weak secret.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:86400000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a signed JWT with tenantId and userId claims.
     * Used by generate-token.sh and integration tests — not called at runtime by the API itself.
     */
    public String generateToken(String tenantId, String userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("tenantId", tenantId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims extractClaims(String token) {
        // parseClaimsJws also validates expiration — throws JwtException if expired/tampered.
        // WHY Jwts.parser() not Jwts.parserBuilder(): jjwt 0.12.x replaced parserBuilder()
        // with the fluent Jwts.parser().verifyWith(...).build() API.
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }
}
