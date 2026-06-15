package com.netia.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-tenant rate limiting filter backed by Redis.
 *
 * WHY Redis-backed (not in-memory ConcurrentHashMap):
 *   In a multi-replica deployment (Kubernetes HPA) each pod would have its own
 *   in-memory counter, so a tenant could send N * replica_count requests per
 *   window and bypass the limit entirely. Redis is a shared, atomic store visible
 *   to all replicas — a single source of truth for rate counters.
 *
 * WHY sliding-window via INCR + EXPIRE:
 *   INCR is atomic in Redis (no race conditions between replicas).
 *   EXPIRE is set only on the first increment so the window resets naturally
 *   after one minute without a separate cleanup job.
 *
 * WHY per-tenant (not per-IP):
 *   Behind a corporate NAT or API gateway all clients may share one IP.
 *   Tenant identity is derived from the verified JWT claim, so it cannot be
 *   spoofed and maps directly to the contract SLA level.
 *
 * NOTE (production evolution):
 *   For more sophisticated policies (token bucket, burst allowance) replace this
 *   with Bucket4j's RedisProxyManager or Spring Cloud Gateway's Redis RateLimiter
 *   filter. The current fixed-window approach is simple, correct, and sufficient
 *   for v1 of a Tier-1 API.
 */
@Component
public class RateLimitFilter extends HttpFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String KEY_PREFIX = "rate:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final int requestsPerMinute;

    public RateLimitFilter(
            StringRedisTemplate redis,
            @Value("${rate-limit.requests-per-minute:100}") int requestsPerMinute) {
        this.redis = redis;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // Prefer tenant key (from JWT claim set by JwtAuthenticationFilter);
        // fall back to remote IP for unauthenticated paths (e.g. /actuator/health).
        String tenantId = (String) request.getAttribute("tenantId");
        String key = KEY_PREFIX + (tenantId != null ? tenantId : request.getRemoteAddr());

        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            // Redis unavailable — fail open to avoid blocking all traffic.
            log.warn("Redis unavailable for rate limiting, allowing request through");
            chain.doFilter(request, response);
            return;
        }

        if (count == 1) {
            // First request in this window: set TTL so the key auto-expires.
            redis.expire(key, WINDOW);
        }

        if (count > requestsPerMinute) {
            log.warn("Rate limit exceeded for key={} count={}", key, count);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests. Retry after 1 minute.\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
