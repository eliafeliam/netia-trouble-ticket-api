package com.netia.common.config;

import com.netia.common.security.JwtAuthenticationFilter;
import com.netia.common.security.RateLimitFilter;
import com.netia.common.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit servlet filter registration with deterministic ordering.
 *
 * WHY explicit FilterRegistrationBean instead of relying on @Order:
 *   When filters are @Component beans, Spring Boot auto-registers them at order Integer.MAX_VALUE
 *   (last). FilterRegistrationBean gives us explicit numeric ordering that is easy to read and
 *   change, and makes the filter chain visible in one place.
 *
 * Filter execution order and rationale:
 *
 *   Order 1 — RequestIdFilter:
 *     Must run first so that X-Request-ID is in MDC for ALL subsequent log lines,
 *     including authentication errors. Also echoes the ID in the response header.
 *
 *   Order 2 — JwtAuthenticationFilter:
 *     Parses Bearer token and populates SecurityContext + request.setAttribute("tenantId").
 *     Must run before rate limiting so the rate limiter can key on tenantId (not IP).
 *
 *   Order 3 — RateLimitFilter:
 *     Uses tenantId from request attribute (set by step 2) to apply per-tenant Redis counters.
 *     Running after JWT means authenticated tenants get their SLA-level limit; unauthenticated
 *     requests fall back to IP-based limiting.
 *
 * NOTE: JwtAuthenticationFilter is ALSO registered in the Spring Security filter chain via
 * SecurityConfiguration.addFilterBefore(). The FilterRegistrationBean here registers it for
 * the plain servlet container context (outside Spring Security), ensuring it runs on ALL paths
 * including /actuator and /auth. The SecurityConfiguration registration controls the position
 * within the Security filter chain specifically.
 */
@Configuration
@RequiredArgsConstructor
public class FilterConfiguration {

    private final RequestIdFilter requestIdFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
        FilterRegistrationBean<RequestIdFilter> reg = new FilterRegistrationBean<>(requestIdFilter);
        reg.setOrder(1);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        reg.setOrder(2);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(rateLimitFilter);
        reg.setOrder(3);  // after JWT so tenantId is available for Redis key
        return reg;
    }
}
