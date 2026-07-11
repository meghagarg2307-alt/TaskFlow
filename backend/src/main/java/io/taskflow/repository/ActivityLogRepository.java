package io.taskflow.repository;

import io.taskflow.domain.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    /**
     * Tenant-wide feed, newest first. Backed by {@code idx_activity_org_created}.
     * We pre-join the {@code actor} so the feed renders without N+1 user lookups.
     */
    @Query(value = """
            select a from ActivityLog a
              left join fetch a.actor
             where a.organization.id = :organizationId
            """,
            countQuery = "select count(a) from ActivityLog a where a.organization.id = :organizationId")
    Page<ActivityLog> findFeed(UUID organizationId, Pageable pageable);

    @Query(value = """
            select a from ActivityLog a
              left join fetch a.actor
             where a.organization.id = :organizationId
               and a.boardId = :boardId
            """,
            countQuery = """
                select count(a) from ActivityLog a
                 where a.organization.id = :organizationId
                   and a.boardId = :boardId
                """)
    Page<ActivityLog> findBoardFeed(UUID organizationId, UUID boardId, Pageable pageable);
}
