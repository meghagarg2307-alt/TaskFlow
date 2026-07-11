package io.taskflow.websocket;

import io.taskflow.dto.event.BoardEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link BoardEvent}s to Redis. From there, {@link StompFanoutSubscriber}
 * on <em>every</em> backend instance (including this one) picks them up and forwards
 * to their local STOMP subscribers.
 *
 * <p><b>Why fan out through Redis even when we have only one instance?</b> Because the
 * topology dictates this is the only correct way to scale. If we used both
 * "send-local-now" <em>and</em> Redis fan-out, a multi-instance deployment would
 * double-deliver to clients on the originator. By always going through Redis, the
 * code is identical for 1, 5, or 50 backend instances.</p>
 *
 * <p>The Redis channel name is intentionally tenant-agnostic: events carry their own
 * org/board ids, and the local subscriber resolves the correct STOMP destination.
 * A per-tenant channel would needlessly multiply pub/sub overhead.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardEventBroadcaster {

    public static final String REDIS_CHANNEL = "taskflow:events";

    private final RedisTemplate<String, BoardEvent> redis;

    public void broadcast(BoardEvent event) {
        try {
            redis.convertAndSend(REDIS_CHANNEL, event);
            log.debug("Published {} to Redis (board={}, trace={})",
                    event.type(), event.boardId(), event.traceId());
        } catch (Exception ex) {
            // Broadcasting failures must never bubble — the DB write has already
            // committed. Loud log + carry on; next page reload reconciles.
            log.warn("Failed to publish event to Redis", ex);
        }
    }
}
