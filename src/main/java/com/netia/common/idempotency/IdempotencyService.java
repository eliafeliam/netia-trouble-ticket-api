package com.netia.common.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed idempotency store for create operations.
 *
 * WHY Redis (not database):
 *   A DB-based idempotency table would work but adds schema migrations and DB load.
 *   Redis provides sub-millisecond atomics (SETNX) and automatic TTL expiry — ideal
 *   for short-lived idempotency windows. The DB unique constraint is the fallback for
 *   when Redis is temporarily unavailable.
 *
 * Key lifecycle:
 *   1. claim(key)       → SETNX key "IN_PROGRESS" TTL 24h   (atomic lock)
 *   2. storeResult(key) → SET  key "<ticketId>"   TTL 24h   (persist result)
 *   3. get(key)         → GET  key                           (check on retry)
 *   4. remove(key)      → DEL  key                           (on processing error)
 *
 * WHY 24h TTL:
 *   Network retries typically happen within seconds. 24h is generous enough to cover
 *   any reasonable retry window while preventing unbounded Redis memory growth.
 *   After 24h the key expires automatically — no cleanup job required.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    /**
     * Returns the stored value for the given key.
     * Value is either "IN_PROGRESS" (processing ongoing) or a ticketId (finished).
     */
    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key));
        } catch (Exception e) {
            log.warn("Redis unavailable on get key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public boolean claim(String key) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "IN_PROGRESS", TTL));
        } catch (Exception e) {
            log.warn("Redis unavailable on claim key={}: {}", key, e.getMessage());
            return true; // fail open: let the request proceed, DB constraint is the safety net
        }
    }

    public void storeResult(String key, String ticketId) {
        try {
            redis.opsForValue().set(key, ticketId, TTL);
        } catch (Exception e) {
            log.warn("Redis unavailable on storeResult key={}: {}", key, e.getMessage());
        }
    }

    public void remove(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis unavailable on remove key={}: {}", key, e.getMessage());
        }
    }
}
