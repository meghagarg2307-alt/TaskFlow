package io.taskflow.service.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Atomic, fixed-window rate limiter backed by Redis.
 *
 * <p>The Lua script in {@link io.taskflow.config.RedisConfig#rateLimitScript()}
 * {@code INCR}s the counter and sets {@code PEXPIRE} on the first hit. Atomicity is
 * vital — without it, two simultaneous logins could both pass the limit because
 * each sees the counter at zero before the other has incremented it.</p>
 *
 * <p>Keys are prefixed with {@code taskflow:rl:} so they're easy to monitor:</p>
 * <pre>redis-cli --scan --pattern 'taskflow:rl:*'</pre>
 *
 * <p>Fail-open on Redis errors: if Redis is down, we let requests through. The
 * alternative — failing closed — would turn a Redis outage into a full auth outage,
 * which is worse than a brief loss of brute-force protection. Logged loudly so the
 * outage is visible.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "taskflow:rl:";

    private final StringRedisTemplate redis;
    private final RedisScript<Long> rateLimitScript;

    @Override
    public boolean tryAcquire(String key, long limit, Duration window) {
        String redisKey = KEY_PREFIX + key;
        try {
            Long count = redis.execute(
                    rateLimitScript,
                    List.of(redisKey),
                    String.valueOf(window.toMillis()));
            if (count == null) {
                // Lua script returned nil somehow; treat as allowed.
                return true;
            }
            return count <= limit;
        } catch (Exception ex) {
            log.error("Rate limiter check failed (failing OPEN for key={})", key, ex);
            return true;
        }
    }
}
