package io.taskflow.config;

import io.taskflow.websocket.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket configuration.
 *
 * <p><b>Endpoint:</b> {@code /ws}. Uses raw WebSocket (no SockJS) — modern browsers
 * universally support WebSocket; SockJS adds 50KB of client code and ~10 fallback
 * transports that we'd never exercise. If we later need to support corporate proxies
 * that block WS, SockJS can be enabled with a one-line change.</p>
 *
 * <p><b>Broker:</b> Simple in-memory broker for v1 — fine for a single backend instance.
 * Step 5 replaces this with Redis pub/sub for multi-instance fan-out; the topic
 * conventions stay identical so clients are unaffected.</p>
 *
 * <p><b>Heartbeats:</b> 10s in each direction. The broker drops sessions whose peer
 * misses two consecutive heartbeats — important for cleaning up dead connections
 * behind load balancers that silently drop idle TCP streams.</p>
 *
 * <p><b>CORS:</b> {@code allowedOrigins} (not {@code allowedOriginPatterns}) — required
 * for credential-bearing requests, and the Angular dev origin is fixed.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;
    private final StompAuthChannelInterceptor authInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Outbound destinations the client can subscribe to.
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{ properties.heartbeatIntervalMs(),
                                                properties.heartbeatIntervalMs() })
                .setTaskScheduler(heartbeatScheduler());

        // Inbound destination prefix for @MessageMapping endpoints (not used in v1,
        // but the convention is in place for future RPC-style frames).
        registry.setApplicationDestinationPrefixes("/app");

        // Convention for per-user destinations resolved by the user's session id.
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Auth interceptor MUST be on the inbound channel so CONNECT/SUBSCRIBE are
        // intercepted before any business handler ever sees the message.
        registration.interceptors(authInterceptor);
    }

    /**
     * Dedicated single-thread scheduler for STOMP heartbeats. Default behaviour uses
     * Spring's shared scheduler which can starve heartbeats under load — a separate
     * thread keeps liveness signaling reliable.
     */
    private ThreadPoolTaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}
