package com.netia.common.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Immutable security principal that carries the two tenant-scoping claims from the JWT.
 *
 * WHY implements UserDetails (not a plain POJO):
 *   Spring Security's UsernamePasswordAuthenticationToken requires a principal that
 *   implements UserDetails so that framework utilities (e.g. @AuthenticationPrincipal,
 *   SecurityContextHolder helpers) can retrieve it in a type-safe way.
 *
 * WHY only userId + tenantId (not roles here):
 *   Roles/authorities are passed separately as the third constructor argument to
 *   UsernamePasswordAuthenticationToken in JwtAuthenticationFilter. Mixing them into the
 *   principal would duplicate state and make role checks harder to reason about.
 *
 * WHY isAccountNonExpired / isAccountNonLocked / isEnabled all return true:
 *   These flags are meaningful for database-backed UserDetailsService flows (e.g. checking
 *   a "locked" column). For JWT-based auth, token expiry is validated by JwtTokenProvider
 *   (parseClaimsJws throws on expired token) — by the time we construct this principal the
 *   token is already known to be valid.
 */
@Getter
public class TenantAwarePrincipal implements UserDetails {

    private final String userId;
    private final String tenantId;

    public TenantAwarePrincipal(String userId, String tenantId) {
        this.userId   = userId;
        this.tenantId = tenantId;
    }

    /** Authorities are managed by JwtAuthenticationFilter, not stored here. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override public String  getPassword()              { return null; }
    @Override public String  getUsername()              { return userId; }
    @Override public boolean isAccountNonExpired()      { return true; }
    @Override public boolean isAccountNonLocked()       { return true; }
    @Override public boolean isCredentialsNonExpired()  { return true; }
    @Override public boolean isEnabled()                { return true; }
}
