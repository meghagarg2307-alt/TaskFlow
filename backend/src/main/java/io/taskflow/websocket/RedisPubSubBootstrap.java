package io.taskflow.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Starts {@link RedisMessageListenerContainer} after the web context is up.
 *
 * <p>Spring Data Redis 2.7+ fails the entire application if the listener container
 * cannot subscribe during Lifecycle {@code start()}. Upstash (TLS) and cold
 * networks make that fragile on Render. Deferring start keeps the API available
 * while Pub/Sub reconnects in the background.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPubSubBootstrap {

    private final RedisMessageListenerContainer container;

    @Value("${taskflow.redis.pubsub.startup-max-attempts:12}")
    private int maxAttempts;

    @Value("${taskflow.redis.pubsub.startup-retry-delay-ms:5000}")
    private long retryDelayMs;

    @EventListener(ApplicationReadyEvent.class)
    public void startListener() {
        Thread starter = new Thread(this::startWithRetry, "redis-pubsub-bootstrap");
        starter.setDaemon(true);
        starter.start();
    }

    private void startWithRetry() {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (container.isRunning()) {
                log.info("Redis Pub/Sub listener already running");
                return;
            }
            try {
                container.start();
                log.info("Redis Pub/Sub listener started (attempt {}/{})", attempt, maxAttempts);
                return;
            } catch (Exception ex) {
                log.warn("Redis Pub/Sub listener start failed (attempt {}/{}): {}",
                        attempt, maxAttempts, ex.getMessage());
                if (attempt == maxAttempts) {
                    log.error("Redis Pub/Sub listener gave up after {} attempts — "
                            + "real-time fan-out will be offline until restart. "
                            + "REST API remains available.", maxAttempts, ex);
                    return;
                }
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
