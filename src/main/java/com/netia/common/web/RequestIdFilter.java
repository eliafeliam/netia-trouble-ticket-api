package com.netia.common.web;

import com.netia.common.util.RequestIdHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a unique Request-ID to every request and propagates it through MDC.
 *
 * WHY X-Request-ID:
 *   Distributed systems produce logs across many pods. Without a correlation ID it is
 *   impossible to reconstruct a single request's journey from ingress through service
 *   to DB in Kibana/Loki. X-Request-ID is the de-facto standard header for this.
 *
 * WHY accept client-supplied ID (passthrough):
 *   API gateways and load balancers often inject their own X-Request-ID. Accepting it
 *   preserves end-to-end traceability across the full stack (gateway → API → DB logs).
 *   If absent, we generate one so the ID is always present in logs.
 *
 * WHY MDC (Mapped Diagnostic Context):
 *   MDC is a thread-local map that Logback reads automatically. Every log line emitted
 *   during this request will include [requestId] and [tenantId] without any per-call
 *   boilerplate. In Kibana/Loki you can filter by requestId to see the full trace of
 *   one request across thousands of concurrent log lines.
 *
 * WHY finally block to clear MDC:
 *   MDC entries are thread-local. With Java 21 virtual threads and HikariCP connection
 *   pooling, threads are reused across requests. Without cleanup, a subsequent request
 *   on the same thread would inherit the previous request's IDs — a subtle data leak.
 *
 * Filter order: 1 (first) — request ID must be available to all downstream filters and
 * controllers, especially for error logging before authentication.
 */
@Slf4j
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        }

        RequestIdHolder.setRequestId(requestId);
        response.addHeader(REQUEST_ID_HEADER, requestId);
        MDC.put("requestId", requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Must run even if the filter chain throws — prevents MDC / ThreadLocal leaks.
            MDC.remove("requestId");
            MDC.remove("tenantId");  // set by JwtAuthenticationFilter, cleared here
            RequestIdHolder.clear();
        }
    }
}
