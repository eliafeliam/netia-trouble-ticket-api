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
 * Filter execution order:
 *   Order 1 — RequestIdFilter: X-Request-ID in MDC for all log lines.
 *   Order 2 — RateLimitFilter: per-tenant Redis counters (tenantId set by Security chain).
 *
 * JwtAuthenticationFilter is registered ONLY in the Spring Security filter chain
 * (SecurityConfiguration.addFilterBefore). Its servlet-level auto-registration is
 * explicitly disabled here to prevent double execution.
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

    /**
     * Disable servlet-level auto-registration of JwtAuthenticationFilter.
     * It is already positioned in the Spring Security filter chain via
     * SecurityConfiguration.addFilterBefore(). Without this, the filter
     * would execute twice per request — once as a plain servlet filter
     * and once inside the Security chain.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterAutoRegistrationDisabled() {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(rateLimitFilter);
        reg.setOrder(2);
        return reg;
    }
}
