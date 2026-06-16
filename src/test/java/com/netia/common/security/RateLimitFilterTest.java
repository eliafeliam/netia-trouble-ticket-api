package com.netia.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter — unit tests")
class RateLimitFilterTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        filter = new RateLimitFilter(redis, 3);
    }

    private MockHttpServletRequest request(String tenantId) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (tenantId != null) req.setAttribute("tenantId", tenantId);
        req.setRemoteAddr("127.0.0.1");
        return req;
    }

    @Test
    @DisplayName("within limit: request passes through, chain continues")
    void withinLimit_chainContinues() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("tenant-A"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("exactly at limit: request still passes through")
    void atLimit_requestPasses() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(3L);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("tenant-A"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("one over limit: returns 429 RATE_LIMIT_EXCEEDED")
    void overLimit_returns429() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(4L);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(request("tenant-A"), resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(resp.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("far over limit: still returns 429")
    void farOverLimit_returns429() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1000L);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(request("tenant-A"), resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("first request in window: sets TTL on Redis key")
    void firstRequest_setsTtl() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);

        filter.doFilter(request("tenant-A"), new MockHttpServletResponse(), new MockFilterChain());

        verify(redis).expire(anyString(), any());
    }

    @Test
    @DisplayName("subsequent requests: TTL not reset")
    void subsequentRequest_doesNotResetTtl() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(2L);

        filter.doFilter(request("tenant-A"), new MockHttpServletResponse(), new MockFilterChain());

        verify(redis, never()).expire(anyString(), any());
    }

    @Test
    @DisplayName("Redis returns null: fail-open, request passes through")
    void redisReturnsNull_failOpen() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(null);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("tenant-A"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("no tenantId attribute: uses remote IP as key (unauthenticated paths)")
    void noTenantId_usesRemoteIp() throws Exception {
        when(valueOps.increment("rate:127.0.0.1")).thenReturn(1L);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(request(null), resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(valueOps).increment("rate:127.0.0.1");
    }

    @Test
    @DisplayName("tenant isolation: different tenants have independent counters")
    void tenantIsolation_separateKeys() throws Exception {
        when(valueOps.increment("rate:tenant-A")).thenReturn(1L);
        when(valueOps.increment("rate:tenant-B")).thenReturn(1L);

        filter.doFilter(request("tenant-A"), new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(request("tenant-B"), new MockHttpServletResponse(), new MockFilterChain());

        verify(valueOps).increment("rate:tenant-A");
        verify(valueOps).increment("rate:tenant-B");
    }

    @Test
    @DisplayName("429 response body is valid JSON with code and message fields")
    void rateLimitExceeded_responseBodyIsValidJson() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(999L);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(request("tenant-A"), resp, new MockFilterChain());

        String body = resp.getContentAsString();
        assertThat(body).contains("\"code\"").contains("\"message\"");
        assertThat(resp.getContentType()).contains("application/json");
    }
}
