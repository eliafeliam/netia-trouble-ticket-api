package com.netia.common.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis connection configuration using Lettuce.
 *
 * WHY Lettuce (not Jedis):
 *   Lettuce is the default Redis client in Spring Boot 3.x. It is non-blocking and
 *   thread-safe by design — a single shared connection can serve all virtual threads
 *   concurrently without a separate connection pool, which aligns well with Java 21
 *   Virtual Threads. Jedis is blocking and requires a separate connection pool.
 *
 * WHY Redis in this project:
 *   Redis serves two purposes:
 *   1. Idempotency store (IdempotencyService) — atomic SETNX prevents duplicate ticket
 *      creation across replicas without relying solely on DB constraints.
 *   2. Rate limiting (RateLimitFilter) — shared INCR counters enforce per-tenant limits
 *      across all replicas. In-memory counters (ConcurrentHashMap) would be per-pod only,
 *      allowing N * replica_count requests per window.
 *
 * WHY StringRedisTemplate (not generic RedisTemplate<Object,Object>):
 *   All keys and values in this project are plain strings (ticket IDs, "IN_PROGRESS",
 *   rate counters). StringRedisTemplate avoids Java serialisation overhead, stores
 *   human-readable values, and is simpler to inspect with redis-cli in production.
 *
 * PRODUCTION NOTE:
 *   For high-availability, replace RedisStandaloneConfiguration with
 *   RedisSentinelConfiguration (for automatic failover) or RedisClusterConfiguration
 *   (for horizontal sharding). Both are drop-in replacements for this bean.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
