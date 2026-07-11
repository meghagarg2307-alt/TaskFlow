package io.taskflow.service.cache;

import io.taskflow.domain.enums.OrganizationRole;

import java.io.Serializable;
import java.util.UUID;

/**
 * Plain serializable projection of {@code OrganizationMembership} for Redis cache.
 *
 * <p>We don't cache JPA entities directly — they carry Hibernate proxies whose
 * serialized form depends on session state, which leads to subtle deserialization
 * bugs when the cache is read from a different thread/transaction. A flat record
 * is the safe choice.</p>
 */
public record MembershipView(
        UUID userId,
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        OrganizationRole role
) implements Serializable {}
