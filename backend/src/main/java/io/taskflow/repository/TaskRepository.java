package io.taskflow.repository;

import io.taskflow.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    /**
     * Bulk fetch all tasks for a board, ordered for direct rendering. Uses the
     * partial covering index {@code idx_tasks_board_column_position} — single index
     * scan, no sort step.
     */
    @Query("""
            select t from Task t
              left join fetch t.assignee
             where t.board.id = :boardId
               and t.organization.id = :organizationId
               and t.deletedAt is null
             order by t.column.id, t.position
            """)
    List<Task> findBoardSnapshot(UUID boardId, UUID organizationId);

    @Query("""
            select t from Task t
              join fetch t.board b
              join fetch b.project
              join fetch t.column
              left join fetch t.assignee
             where t.id = :id
               and t.organization.id = :organizationId
               and t.deletedAt is null
            """)
    Optional<Task> findActiveByIdAndOrg(UUID id, UUID organizationId);

    /** Max position in a column — appending a new task. */
    @Query("""
            select coalesce(max(t.position), 0)
              from Task t
             where t.column.id = :columnId
               and t.deletedAt is null
            """)
    long maxPositionInColumn(UUID columnId);

    @Query("""
            select t from Task t
              join fetch t.board b
              join fetch b.project
             where t.organization.id = :organizationId
               and t.deletedAt is not null
               and b.deletedAt is null
               and b.project.deletedAt is null
             order by t.deletedAt desc
            """)
    List<Task> findDeletedRoots(UUID organizationId);

    @Query("""
            select t from Task t
              join fetch t.board b
              join fetch b.project
             where t.id = :id
               and t.organization.id = :organizationId
               and t.deletedAt is not null
            """)
    Optional<Task> findDeletedByIdAndOrg(UUID id, UUID organizationId);

    @Query("""
            select t from Task t
              join fetch t.organization
             where t.deletedAt is not null
               and t.deletedAt < :cutoff
               and t.organization.deletedAt is null
               and t.board.deletedAt is null
               and t.board.project.deletedAt is null
            """)
    List<Task> findExpiredDeletedTasks(Instant cutoff);
}
