package com.netia.common.idempotency;

import lombok.RequiredArgsConstructor;
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
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    /**
     * Atomically claims the key for processing using SETNX (Set if Not eXists).
     *
     * WHY setIfAbsent (SETNX):
     *   SETNX is atomic in Redis — only one caller gets true even under concurrent load.
     *   This prevents two replicas from both believing they own the key and both proceeding
     *   to create the ticket, defeating the purpose of idempotency.
     *
     * @return true if this caller claimed the key (may proceed), false if another replica already owns it.
     */
    public boolean claim(String key) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "IN_PROGRESS", TTL));
    }

    /**
     * Overwrites the key with the finished ticketId, replacing "IN_PROGRESS".
     * Subsequent retries will find the ticketId and return it without re-processing.
     */
    public void storeResult(String key, String ticketId) {
        redis.opsForValue().set(key, ticketId, TTL);
    }

    /**
     * Deletes the key. Called when processing fails so the next retry can re-claim it.
     * Without this, a failed first attempt would leave the key as "IN_PROGRESS" forever
     * (until TTL expiry), blocking all retries for 24h.
     */
    public void remove(String key) {
        redis.delete(key);
    }
}
