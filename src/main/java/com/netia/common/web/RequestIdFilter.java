package com.netia.common.web;

import com.netia.common.util.RequestIdHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;

@Slf4j
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = RequestIdHolder.generateRequestId();
        } else {
            RequestIdHolder.setRequestId(requestId);
        }

        response.addHeader(REQUEST_ID_HEADER, requestId);

        try {
            // Put request id into MDC for structured logging and include tenantId if present
            MDC.put("requestId", requestId);
            String tenantId = (String) request.getAttribute("tenantId");
            if (tenantId != null) {
                MDC.put("tenantId", tenantId);
            }

            filterChain.doFilter(request, response);
        } finally {
            RequestIdHolder.clear();
            // Clear MDC entries we added
            MDC.remove("requestId");
            MDC.remove("tenantId");
        }
    }
}


