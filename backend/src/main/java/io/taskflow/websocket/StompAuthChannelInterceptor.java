package io.taskflow.websocket;

import io.taskflow.common.tenant.TenantContext;
import io.taskflow.exception.UnauthorizedException;
import io.taskflow.repository.BoardRepository;
import io.taskflow.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inbound STOMP channel interceptor — the single source of truth for WebSocket auth.
 *
 * <ul>
 *   <li>{@code CONNECT}: validates the {@code Authorization: Bearer <jwt>} header,
 *       binds a {@link StompPrincipal} to the session.</li>
 *   <li>{@code SUBSCRIBE}: parses the destination and enforces tenant scope:
 *       <ul>
 *         <li>{@code /topic/orgs/{orgId}} → orgId must match the session principal</li>
 *         <li>{@code /topic/boards/{boardId}} → board must belong to the session org</li>
 *         <li>{@code /user/queue/**} → always allowed (already user-scoped by STOMP itself)</li>
 *       </ul></li>
 *   <li>{@code DISCONNECT}: no action — STOMP cleans the session automatically.</li>
 * </ul>
 *
 * <p>Throwing {@link MessagingException} causes STOMP to send an ERROR frame to the
 * client and close the connection — the right failure mode for both forged tokens
 * and unauthorized subscriptions.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    public static final String TOPIC_PREFIX = "/topic/";
    public static final String USER_QUEUE_PREFIX = "/user/";
    private static final String BEARER = "Bearer ";

    // Destinations we know how to authorize. Anything else under /topic is rejected
    // — better to deny by default than to leak a future topic accidentally.
    private static final Pattern ORG_TOPIC   = Pattern.compile("^/topic/orgs/([0-9a-fA-F-]{36})$");
    private static final Pattern BOARD_TOPIC = Pattern.compile("^/topic/boards/([0-9a-fA-F-]{36})$");

    private final JwtService jwtService;
    private final BoardRepository boardRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT    -> authenticateConnect(accessor);
            case SUBSCRIBE  -> authorizeSubscribe(accessor);
            default         -> { /* SEND / DISCONNECT / ACK / NACK — no action */ }
        }
        return message;
    }

    // ----------------------------------------------------------------- CONNECT

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String header = firstNativeHeader(accessor, HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessagingException("CONNECT missing Authorization: Bearer <token>");
        }
        String token = header.substring(BEARER.length()).trim();

        JwtService.AccessTokenClaims claims;
        try {
            claims = jwtService.parseAccessToken(token);
        } catch (UnauthorizedException ex) {
            // Keep STOMP error generic — never tell the attacker which check failed.
            throw new MessagingException("Invalid access token");
        }

        StompPrincipal principal = new StompPrincipal(
                claims.userId(), claims.organizationId(), claims.role());
        accessor.setUser(principal);
        // Note: we don't bind TenantContext here — the WS thread doesn't run any
        // tenant-scoped service code on inbound frames. All writes still go through REST.
        log.debug("WS CONNECT authenticated user={} org={} session={}",
                principal.userId(), principal.organizationId(), accessor.getSessionId());
    }

    // --------------------------------------------------------------- SUBSCRIBE

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        StompPrincipal principal = (StompPrincipal) accessor.getUser();
        if (principal == null) {
            throw new MessagingException("SUBSCRIBE before CONNECT");
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessagingException("SUBSCRIBE without destination");
        }

        // User-scoped destinations are auto-restricted by STOMP — Spring rewrites
        // /user/queue/X → /queue/X-user{sessionId}. Safe to allow.
        if (destination.startsWith(USER_QUEUE_PREFIX)) {
            return;
        }

        Matcher orgMatcher = ORG_TOPIC.matcher(destination);
        if (orgMatcher.matches()) {
            UUID requestedOrg = UUID.fromString(orgMatcher.group(1));
            if (!requestedOrg.equals(principal.organizationId())) {
                throw new MessagingException("Forbidden: cross-tenant subscription denied");
            }
            return;
        }

        Matcher boardMatcher = BOARD_TOPIC.matcher(destination);
        if (boardMatcher.matches()) {
            UUID boardId = UUID.fromString(boardMatcher.group(1));
            // Verify the board is in the session's org. One indexed PK lookup; cheap.
            boolean ok = boardRepository.findActiveByIdAndOrg(boardId, principal.organizationId())
                    .isPresent();
            if (!ok) {
                throw new MessagingException("Forbidden: board not found in your organization");
            }
            return;
        }

        // Deny-by-default for anything else under /topic/**.
        if (destination.startsWith(TOPIC_PREFIX)) {
            throw new MessagingException("Forbidden: unknown topic " + destination);
        }
        // App-prefix (/app/**) is allowed (no @MessageMapping endpoints in v1, but
        // permitting it future-proofs RPC-style send-and-receive flows).
    }

    private static String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        // Native headers come as a List<String> on the wire; we want the first value.
        var values = accessor.getNativeHeader(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
