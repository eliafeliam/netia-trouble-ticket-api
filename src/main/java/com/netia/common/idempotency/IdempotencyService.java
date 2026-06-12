package com.netia.common.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    // TTL for idempotency keys
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    /**
     * Returns stored value for key if present
     */
    public Optional<String> get(String key) {
        String v = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(v);
    }

    /**
     * Try to claim key for processing. Returns true if claim succeeded (key was absent and now set to "IN_PROGRESS").
     */
    public boolean claim(String key) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "IN_PROGRESS", IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Store result for key (e.g., ticketId)
     */
    public void storeResult(String key, String ticketId) {
        redisTemplate.opsForValue().set(key, ticketId, IDEMPOTENCY_TTL);
    }

    /**
     * Remove key (on failure)
     */
    public void remove(String key) {
        redisTemplate.delete(key);
    }
}

