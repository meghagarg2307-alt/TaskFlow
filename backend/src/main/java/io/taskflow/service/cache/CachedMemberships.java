package io.taskflow.service.cache;

import java.io.Serializable;
import java.util.List;

/**
 * Wrapper so Redis/Jackson cache round-trips {@link MembershipView} lists with a
 * concrete type (avoids {@code List<LinkedHashMap>} {@link ClassCastException} on login).
 */
public record CachedMemberships(List<MembershipView> items) implements Serializable {}
