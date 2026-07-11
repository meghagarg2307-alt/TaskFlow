package io.taskflow.service.ratelimit;

import java.time.Duration;

public interface RateLimiter {

    /**
     * Increment the counter for {@code key} and return whether the caller may proceed.
     * Implementations must be atomic across processes — Redis Lua, not Java locks.
     *
     * @param key      unique throttle bucket (e.g. {@code "login:email:foo@x.com"})
     * @param limit    max calls allowed within {@code window}
     * @param window   sliding/fixed window duration
     * @return {@code true} if under the limit; {@code false} if quota exceeded
     */
    boolean tryAcquire(String key, long limit, Duration window);
}
