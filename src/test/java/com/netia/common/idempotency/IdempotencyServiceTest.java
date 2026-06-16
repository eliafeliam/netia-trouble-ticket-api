package com.netia.common.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService — unit tests")
class IdempotencyServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(redis);
    }

    private void stubValueOps() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("get: returns value when key exists")
    void get_keyExists_returnsValue() {
        stubValueOps();
        when(valueOps.get("key-1")).thenReturn("TT-123");

        assertThat(service.get("key-1")).contains("TT-123");
    }

    @Test
    @DisplayName("get: returns empty when key not found")
    void get_keyMissing_returnsEmpty() {
        stubValueOps();
        when(valueOps.get("key-1")).thenReturn(null);

        assertThat(service.get("key-1")).isEmpty();
    }

    @Test
    @DisplayName("get: Redis unavailable → returns empty (fail-safe, no exception)")
    void get_redisDown_returnsEmptyWithoutException() {
        stubValueOps();
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        assertThatNoException().isThrownBy(() -> {
            Optional<String> result = service.get("key-1");
            assertThat(result).isEmpty();
        });
    }

    // ── claim ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("claim: returns true when key successfully claimed (SETNX)")
    void claim_keyNotExists_returnsTrue() {
        stubValueOps();
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        assertThat(service.claim("key-1")).isTrue();
    }

    @Test
    @DisplayName("claim: returns false when key already claimed by another replica")
    void claim_keyAlreadyClaimed_returnsFalse() {
        stubValueOps();
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        assertThat(service.claim("key-1")).isFalse();
    }

    @Test
    @DisplayName("claim: Redis returns null → treated as false (not claimed)")
    void claim_redisReturnsNull_returnsFalse() {
        stubValueOps();
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(null);

        assertThat(service.claim("key-1")).isFalse();
    }

    @Test
    @DisplayName("claim: Redis unavailable → fail-open (returns true, DB constraint is safety net)")
    void claim_redisDown_failOpenReturnsTrue() {
        stubValueOps();
        when(valueOps.setIfAbsent(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        assertThatNoException().isThrownBy(() -> {
            boolean result = service.claim("key-1");
            assertThat(result).isTrue();
        });
    }

    // ── storeResult ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("storeResult: stores ticketId with TTL")
    void storeResult_storesWithTtl() {
        stubValueOps();
        service.storeResult("key-1", "TT-123");

        verify(valueOps).set(eq("key-1"), eq("TT-123"), any());
    }

    @Test
    @DisplayName("storeResult: Redis unavailable → no exception thrown (fire-and-forget)")
    void storeResult_redisDown_noException() {
        stubValueOps();
        doThrow(new RuntimeException("Redis down")).when(valueOps).set(anyString(), anyString(), any());

        assertThatNoException().isThrownBy(() -> service.storeResult("key-1", "TT-123"));
    }

    // ── remove ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("remove: deletes key from Redis")
    void remove_deletesKey() {
        service.remove("key-1");

        verify(redis).delete("key-1");
    }

    @Test
    @DisplayName("remove: Redis unavailable → no exception thrown")
    void remove_redisDown_noException() {
        when(redis.delete(anyString())).thenThrow(new RuntimeException("Redis down"));

        assertThatNoException().isThrownBy(() -> service.remove("key-1"));
    }

    // ── contract ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IN_PROGRESS value is detectable via get")
    void get_inProgressValue_returnsInProgress() {
        stubValueOps();
        when(valueOps.get("key-1")).thenReturn("IN_PROGRESS");

        assertThat(service.get("key-1")).contains("IN_PROGRESS");
    }

    @Test
    @DisplayName("full lifecycle: claim → storeResult → get returns ticketId")
    void fullLifecycle_claimThenStoreResult() {
        stubValueOps();
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(valueOps.get("key-1")).thenReturn("TT-999");

        boolean claimed = service.claim("key-1");
        service.storeResult("key-1", "TT-999");
        Optional<String> result = service.get("key-1");

        assertThat(claimed).isTrue();
        assertThat(result).contains("TT-999");
    }
}
