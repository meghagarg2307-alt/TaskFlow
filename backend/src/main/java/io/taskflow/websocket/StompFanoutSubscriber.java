package io.taskflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskflow.dto.event.BoardEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listens to {@link BoardEventBroadcaster#REDIS_CHANNEL} and forwards every event to
 * the local STOMP broker. This is the OTHER half of the cross-instance fan-out: a
 * client connected to instance A receives updates produced by instance B because
 * every instance's listener runs on every published event.
 *
 * <p>This component is the only place that actually calls
 * {@link SimpMessagingTemplate#convertAndSend}. The broadcaster never sends to STOMP
 * directly — that would skip the Redis hop and break multi-instance topology.</p>
 *
 * <p>Topic routing: events with a {@code boardId} go to
 * {@code /topic/boards/{boardId}}; everything else to {@code /topic/orgs/{orgId}}.</p>
 *
 * <p>Subscription is registered here; the container itself is started by
 * {@link RedisPubSubBootstrap} after the application is ready.</p>
 */
@Slf4j
@Component
public class StompFanoutSubscriber implements MessageListener {

    private static final String BOARD_TOPIC_PREFIX = "/topic/boards/";
    private static final String ORG_TOPIC_PREFIX   = "/topic/orgs/";

    private final RedisMessageListenerContainer container;
    private final SimpMessagingTemplate stompTemplate;
    private final ObjectMapper objectMapper;

    public StompFanoutSubscriber(RedisMessageListenerContainer container,
                                 SimpMessagingTemplate stompTemplate,
                                 @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        this.container = container;
        this.stompTemplate = stompTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void register() {
        // ChannelTopic → SUBSCRIBE (matches convertAndSend / PUBLISH). PatternTopic
        // would use PSUBSCRIBE and is the wrong API for a literal channel name.
        container.addMessageListener(this, new ChannelTopic(BoardEventBroadcaster.REDIS_CHANNEL));
        log.info("Registered Redis channel '{}' for STOMP fan-out (container starts after ready)",
                BoardEventBroadcaster.REDIS_CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            BoardEvent event = objectMapper.readValue(message.getBody(), BoardEvent.class);
            String destination = pickDestination(event);
            stompTemplate.convertAndSend(destination, event);
            log.debug("Fanned out {} → {} (trace={})",
                    event.type(), destination, event.traceId());
        } catch (Exception ex) {
            // A poison-pill message must not bring down the listener thread. Log
            // and keep going. (In ops-mode we'd also bump a metric.)
            log.error("Failed to forward Redis event to STOMP", ex);
        }
    }

    private static String pickDestination(BoardEvent event) {
        UUID boardId = event.boardId();
        if (boardId != null) return BOARD_TOPIC_PREFIX + boardId;
        return ORG_TOPIC_PREFIX + event.organizationId();
    }
}
