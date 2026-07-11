package io.taskflow.websocket;

import io.taskflow.domain.enums.OrganizationRole;

import java.security.Principal;
import java.util.UUID;

/**
 * STOMP session principal — what {@code StompHeaderAccessor.getUser()} returns after
 * a successful CONNECT. Bound to the session for its lifetime; every later frame
 * (SUBSCRIBE / SEND / DISCONNECT) is authorized against this.
 *
 * <p>{@link #getName()} returns the user's UUID as a string — STOMP uses {@code name}
 * for user-specific destinations ({@code /user/queue/...}), so it must be unique and
 * stable for the session.</p>
 */
public record StompPrincipal(
        UUID userId,
        UUID organizationId,
        OrganizationRole role
) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
