package io.taskflow.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskflow.config.CacheConfig;
import io.taskflow.domain.OrganizationMembership;
import io.taskflow.repository.OrganizationMembershipRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Caches the "all memberships for a user" lookup — the dominant read in the auth
 * hot path (called on every login, refresh, switch-org).
 *
 * <p><b>Cache key</b>: {@code userId.toString()} → {@code taskflow:cache:userMemberships:<uuid>}.
 * Single-tenant key shape is fine here because memberships are inherently per-user.</p>
 *
 * <p><b>Eviction</b> is precise: when a user's memberships change (added, removed,
 * role updated), only that user's entry is evicted. Org-level admins changing
 * another user's role evict that user too. We never use {@code allEntries=true}
 * — would defeat the cache under load.</p>
 *
 * <p>Cache writes participate in the transaction (see {@code transactionAware} on
 * {@link CacheConfig}). If a role change rolls back, the eviction does not happen,
 * and the cache stays consistent with the DB.</p>
 */
@Service
public class MembershipCache {

    private final OrganizationMembershipRepository repository;
    private final ObjectMapper objectMapper;

    public MembershipCache(OrganizationMembershipRepository repository,
                           @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Public entry — always normalizes cached values so login never hits a
     * {@code LinkedHashMap} cast from legacy Redis entries.
     */
    @Transactional(readOnly = true)
    public List<MembershipView> getMembershipsForUser(UUID userId) {
        return normalize(cachedMembershipsForUser(userId).items());
    }

    @Cacheable(value = CacheConfig.USER_MEMBERSHIPS, key = "#userId.toString()")
    @Transactional(readOnly = true)
    public CachedMemberships cachedMembershipsForUser(UUID userId) {
        List<MembershipView> items = repository.findAllForUser(userId).stream()
                .map(MembershipCache::toView)
                .toList();
        return new CachedMemberships(items);
    }

    private List<MembershipView> normalize(List<?> items) {
        return items.stream()
                .map(o -> o instanceof MembershipView mv
                        ? mv
                        : objectMapper.convertValue(o, MembershipView.class))
                .toList();
    }

    public Optional<MembershipView> findMembership(UUID userId, UUID organizationId) {
        return getMembershipsForUser(userId).stream()
                .filter(m -> m.organizationId().equals(organizationId))
                .findFirst();
    }

    @CacheEvict(value = CacheConfig.USER_MEMBERSHIPS, key = "#userId.toString()")
    public void evict(UUID userId) {
        // Method body intentionally empty — Spring Cache handles the eviction.
    }

    private static MembershipView toView(OrganizationMembership m) {
        return new MembershipView(
                m.getUser().getId(),
                m.getOrganization().getId(),
                m.getOrganization().getName(),
                m.getOrganization().getSlug(),
                m.getRole());
    }
}
