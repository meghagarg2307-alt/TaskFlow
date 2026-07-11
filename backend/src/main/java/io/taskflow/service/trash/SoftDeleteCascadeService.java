package io.taskflow.service.trash;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Bulk soft-delete / restore / hard-delete for hierarchical resources. Uses JPQL updates
 * for consistency and performance inside a single transaction.
 */
@Service
@RequiredArgsConstructor
public class SoftDeleteCascadeService {

    private final EntityManager em;

    @Transactional
    public void softDeleteOrganization(UUID orgId, UUID deletedBy, Instant when) {
        softDeleteCommentsInOrg(orgId, deletedBy, when);
        softDeleteTasksInOrg(orgId, deletedBy, when);
        softDeleteColumnsInOrg(orgId, deletedBy, when);
        softDeleteBoardsInOrg(orgId, deletedBy, when);
        softDeleteProjectsInOrg(orgId, deletedBy, when);
        em.createQuery("""
                update Organization o
                   set o.deletedAt = :when, o.deletedBy = :by,
                       o.restoredAt = null, o.restoredBy = null
                 where o.id = :orgId and o.deletedAt is null
                """)
                .setParameter("orgId", orgId)
                .setParameter("when", when)
                .setParameter("by", deletedBy)
                .executeUpdate();
    }

    @Transactional
    public void restoreOrganization(UUID orgId, UUID restoredBy, Instant when) {
        em.createQuery("""
                update Organization o
                   set o.deletedAt = null, o.deletedBy = null,
                       o.restoredAt = :when, o.restoredBy = :by
                 where o.id = :orgId and o.deletedAt is not null
                """)
                .setParameter("orgId", orgId)
                .setParameter("when", when)
                .setParameter("by", restoredBy)
                .executeUpdate();
        restoreProjectsInOrg(orgId, restoredBy, when);
        restoreBoardsInOrg(orgId, restoredBy, when);
        restoreColumnsInOrg(orgId, restoredBy, when);
        restoreTasksInOrg(orgId, restoredBy, when);
        restoreCommentsInOrg(orgId, restoredBy, when);
    }

    @Transactional
    public void softDeleteProject(UUID projectId, UUID orgId, UUID deletedBy, Instant when) {
        softDeleteCommentsInProject(projectId, orgId, deletedBy, when);
        softDeleteTasksInProject(projectId, orgId, deletedBy, when);
        softDeleteColumnsInProject(projectId, orgId, deletedBy, when);
        softDeleteBoardsInProject(projectId, orgId, deletedBy, when);
        em.createQuery("""
                update Project p
                   set p.deletedAt = :when, p.deletedBy = :by,
                       p.restoredAt = null, p.restoredBy = null
                 where p.id = :projectId and p.organization.id = :orgId and p.deletedAt is null
                """)
                .setParameter("projectId", projectId)
                .setParameter("orgId", orgId)
                .setParameter("when", when)
                .setParameter("by", deletedBy)
                .executeUpdate();
    }

    @Transactional
    public void restoreProject(UUID projectId, UUID orgId, UUID restoredBy, Instant when) {
        em.createQuery("""
                update Project p
                   set p.deletedAt = null, p.deletedBy = null,
                       p.restoredAt = :when, p.restoredBy = :by
                 where p.id = :projectId and p.organization.id = :orgId and p.deletedAt is not null
                """)
                .setParameter("projectId", projectId)
                .setParameter("orgId", orgId)
                .setParameter("when", when)
                .setParameter("by", restoredBy)
                .executeUpdate();
        restoreBoardsInProject(projectId, orgId, restoredBy, when);
        restoreColumnsInProject(projectId, orgId, restoredBy, when);
        restoreTasksInProject(projectId, orgId, restoredBy, when);
        restoreCommentsInProject(projectId, orgId, restoredBy, when);
    }

    @Transactional
    public void softDeleteBoard(UUID boardId, UUID orgId, UUID deletedBy, Instant when) {
        softDeleteCommentsOnBoard(boardId, orgId, deletedBy, when);
        softDeleteTasksOnBoard(boardId, orgId, deletedBy, when);
        softDeleteColumnsOnBoard(boardId, orgId, deletedBy, when);
        em.createQuery("""
                update Board b
                   set b.deletedAt = :when, b.deletedBy = :by,
                       b.restoredAt = null, b.restoredBy = null
                 where b.id = :boardId and b.organization.id = :orgId and b.deletedAt is null
                """)
                .setParameter("boardId", boardId)
                .setParameter("orgId", orgId)
                .setParameter("when", when)
                .setParameter("by", deletedBy)
                .executeUpdate();
    }

    @Transactional
    public void restoreBoard(UUID boardId, UUID orgId, UUID restoredBy, Instant when) {
        em.createQuery("""
                update Board b
                   set b.deletedAt = null, b.deletedBy = null,
                       b.restoredAt = :when, b.restoredBy = :by
                 where b.id = :boardId and b.organization.id = :orgId and b.deletedAt is not null
                """)
                .setParameter("boardId", boardId)
                .setParameter("orgId", orgId)
                .setParameter("when", when)
                .setParameter("by", restoredBy)
                .executeUpdate();
        restoreColumnsOnBoard(boardId, orgId, restoredBy, when);
        restoreTasksOnBoard(boardId, orgId, restoredBy, when);
        restoreCommentsOnBoard(boardId, orgId, restoredBy, when);
    }

    @Transactional
    public void softDeleteColumn(UUID columnId, UUID orgId, UUID deletedBy, Instant when) {
        softDeleteCommentsInColumn(columnId, orgId, deletedBy, when);
        softDeleteTasksInColumn(columnId, orgId, deletedBy, when);
        em.createQuery("""
                update BoardColumn c
                   set c.deletedAt = :when, c.deletedBy = :by,
                       c.restoredAt = null, c.restoredBy = null
                 where c.id = :columnId and c.organization.id = :orgId and c.deletedAt is null
                """)
                .setParameter("columnId", columnId)
                .setParameter("orgId", orgId)
                .setParameter("when", when)
                .setParameter("by", deletedBy)
                .executeUpdate();
    }

    @Transactional
    public void restoreColumn(UUID columnId, UUID orgId, UUID restoredBy, Instant when) {
        em.createQuery("""
                update BoardColumn c
                   set c.deletedAt = null, c.deletedBy = null,
                       c.restoredAt = :when, c.restoredBy = :by
                 where c.id = :columnId and c.organization.id = :orgId and c.deletedAt is not null
                """)
                .setParameter("columnId", columnId)
                .setParameter("orgId", orgId)
                .setParameter("when", when)
                .setParameter("by", restoredBy)
                .executeUpdate();
        restoreTasksInColumn(columnId, orgId, restoredBy, when);
        restoreCommentsInColumn(columnId, orgId, restoredBy, when);
    }

    @Transactional
    public void softDeleteTask(UUID taskId, UUID orgId, UUID deletedBy, Instant when) {
        softDeleteCommentsOnTask(taskId, orgId, deletedBy, when);
        em.createQuery("""
                update Task t
                   set t.deletedAt = :when, t.deletedBy = :by,
                       t.restoredAt = null, t.restoredBy = null
                 where t.id = :taskId and t.organization.id = :orgId and t.deletedAt is null
                """)
                .setParameter("taskId", taskId)
                .setParameter("orgId", orgId)
                .setParameter("when", when)
                .setParameter("by", deletedBy)
                .executeUpdate();
    }

    @Transactional
    public void restoreTask(UUID taskId, UUID orgId, UUID restoredBy, Instant when) {
        em.createQuery("""
                update Task t
                   set t.deletedAt = null, t.deletedBy = null,
                       t.restoredAt = :when, t.restoredBy = :by
                 where t.id = :taskId and t.organization.id = :orgId and t.deletedAt is not null
                """)
                .setParameter("taskId", taskId)
                .setParameter("orgId", orgId)
                .setParameter("when", when)
                .setParameter("by", restoredBy)
                .executeUpdate();
        restoreCommentsOnTask(taskId, orgId, restoredBy, when);
    }

    // --- permanent purge (hard delete) -------------------------------------------------

    @Transactional
    public void permanentlyDeleteOrganization(UUID orgId) {
        em.createQuery("delete from Comment c where c.organization.id = :orgId").setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from Task t where t.organization.id = :orgId").setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from BoardColumn c where c.organization.id = :orgId").setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from Board b where b.organization.id = :orgId").setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from Project p where p.organization.id = :orgId").setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from Organization o where o.id = :orgId").setParameter("orgId", orgId).executeUpdate();
    }

    @Transactional
    public void permanentlyDeleteProject(UUID projectId, UUID orgId) {
        em.createQuery("delete from Comment c where c.task.board.project.id = :projectId and c.organization.id = :orgId")
                .setParameter("projectId", projectId).setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from Task t where t.board.project.id = :projectId and t.organization.id = :orgId")
                .setParameter("projectId", projectId).setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from BoardColumn c where c.board.project.id = :projectId and c.organization.id = :orgId")
                .setParameter("projectId", projectId).setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from Board b where b.project.id = :projectId and b.organization.id = :orgId")
                .setParameter("projectId", projectId).setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from Project p where p.id = :projectId and p.organization.id = :orgId")
                .setParameter("projectId", projectId).setParameter("orgId", orgId).executeUpdate();
    }

    @Transactional
    public void permanentlyDeleteBoard(UUID boardId, UUID orgId) {
        em.createQuery("delete from Comment c where c.task.board.id = :boardId and c.organization.id = :orgId")
                .setParameter("boardId", boardId).setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from Task t where t.board.id = :boardId and t.organization.id = :orgId")
                .setParameter("boardId", boardId).setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from BoardColumn c where c.board.id = :boardId and c.organization.id = :orgId")
                .setParameter("boardId", boardId).setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from Board b where b.id = :boardId and b.organization.id = :orgId")
                .setParameter("boardId", boardId).setParameter("orgId", orgId).executeUpdate();
    }

    @Transactional
    public void permanentlyDeleteTask(UUID taskId, UUID orgId) {
        em.createQuery("delete from Comment c where c.task.id = :taskId and c.organization.id = :orgId")
                .setParameter("taskId", taskId).setParameter("orgId", orgId).executeUpdate();
        em.createQuery("delete from Task t where t.id = :taskId and t.organization.id = :orgId")
                .setParameter("taskId", taskId).setParameter("orgId", orgId).executeUpdate();
    }

    // --- private bulk helpers ----------------------------------------------------------

    private void softDeleteCommentsInOrg(UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Comment c set c.deletedAt = :when, c.deletedBy = :by,
                    c.restoredAt = null, c.restoredBy = null
                 where c.organization.id = :orgId and c.deletedAt is null
                """).setParameter("orgId", orgId).setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteTasksInOrg(UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Task t set t.deletedAt = :when, t.deletedBy = :by,
                    t.restoredAt = null, t.restoredBy = null
                 where t.organization.id = :orgId and t.deletedAt is null
                """).setParameter("orgId", orgId).setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteColumnsInOrg(UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update BoardColumn c set c.deletedAt = :when, c.deletedBy = :by,
                    c.restoredAt = null, c.restoredBy = null
                 where c.organization.id = :orgId and c.deletedAt is null
                """).setParameter("orgId", orgId).setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteBoardsInOrg(UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Board b set b.deletedAt = :when, b.deletedBy = :by,
                    b.restoredAt = null, b.restoredBy = null
                 where b.organization.id = :orgId and b.deletedAt is null
                """).setParameter("orgId", orgId).setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteProjectsInOrg(UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Project p set p.deletedAt = :when, p.deletedBy = :by,
                    p.restoredAt = null, p.restoredBy = null
                 where p.organization.id = :orgId and p.deletedAt is null
                """).setParameter("orgId", orgId).setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreProjectsInOrg(UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Project p set p.deletedAt = null, p.deletedBy = null,
                    p.restoredAt = :when, p.restoredBy = :by
                 where p.organization.id = :orgId and p.deletedAt is not null
                """).setParameter("orgId", orgId).setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreBoardsInOrg(UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Board b set b.deletedAt = null, b.deletedBy = null,
                    b.restoredAt = :when, b.restoredBy = :by
                 where b.organization.id = :orgId and b.deletedAt is not null
                """).setParameter("orgId", orgId).setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreColumnsInOrg(UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update BoardColumn c set c.deletedAt = null, c.deletedBy = null,
                    c.restoredAt = :when, c.restoredBy = :by
                 where c.organization.id = :orgId and c.deletedAt is not null
                """).setParameter("orgId", orgId).setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreTasksInOrg(UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Task t set t.deletedAt = null, t.deletedBy = null,
                    t.restoredAt = :when, t.restoredBy = :by
                 where t.organization.id = :orgId and t.deletedAt is not null
                """).setParameter("orgId", orgId).setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreCommentsInOrg(UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Comment c set c.deletedAt = null, c.deletedBy = null,
                    c.restoredAt = :when, c.restoredBy = :by
                 where c.organization.id = :orgId and c.deletedAt is not null
                """).setParameter("orgId", orgId).setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteCommentsInProject(UUID projectId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Comment c set c.deletedAt = :when, c.deletedBy = :by,
                    c.restoredAt = null, c.restoredBy = null
                 where c.task.board.project.id = :projectId and c.organization.id = :orgId and c.deletedAt is null
                """).setParameter("projectId", projectId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteTasksInProject(UUID projectId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Task t set t.deletedAt = :when, t.deletedBy = :by,
                    t.restoredAt = null, t.restoredBy = null
                 where t.board.project.id = :projectId and t.organization.id = :orgId and t.deletedAt is null
                """).setParameter("projectId", projectId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteColumnsInProject(UUID projectId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update BoardColumn c set c.deletedAt = :when, c.deletedBy = :by,
                    c.restoredAt = null, c.restoredBy = null
                 where c.board.project.id = :projectId and c.organization.id = :orgId and c.deletedAt is null
                """).setParameter("projectId", projectId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteBoardsInProject(UUID projectId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Board b set b.deletedAt = :when, b.deletedBy = :by,
                    b.restoredAt = null, b.restoredBy = null
                 where b.project.id = :projectId and b.organization.id = :orgId and b.deletedAt is null
                """).setParameter("projectId", projectId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreBoardsInProject(UUID projectId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Board b set b.deletedAt = null, b.deletedBy = null,
                    b.restoredAt = :when, b.restoredBy = :by
                 where b.project.id = :projectId and b.organization.id = :orgId and b.deletedAt is not null
                """).setParameter("projectId", projectId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreColumnsInProject(UUID projectId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update BoardColumn c set c.deletedAt = null, c.deletedBy = null,
                    c.restoredAt = :when, c.restoredBy = :by
                 where c.board.project.id = :projectId and c.organization.id = :orgId and c.deletedAt is not null
                """).setParameter("projectId", projectId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreTasksInProject(UUID projectId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Task t set t.deletedAt = null, t.deletedBy = null,
                    t.restoredAt = :when, t.restoredBy = :by
                 where t.board.project.id = :projectId and t.organization.id = :orgId and t.deletedAt is not null
                """).setParameter("projectId", projectId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreCommentsInProject(UUID projectId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Comment c set c.deletedAt = null, c.deletedBy = null,
                    c.restoredAt = :when, c.restoredBy = :by
                 where c.task.board.project.id = :projectId and c.organization.id = :orgId and c.deletedAt is not null
                """).setParameter("projectId", projectId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteCommentsOnBoard(UUID boardId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Comment c set c.deletedAt = :when, c.deletedBy = :by,
                    c.restoredAt = null, c.restoredBy = null
                 where c.task.board.id = :boardId and c.organization.id = :orgId and c.deletedAt is null
                """).setParameter("boardId", boardId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteTasksOnBoard(UUID boardId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Task t set t.deletedAt = :when, t.deletedBy = :by,
                    t.restoredAt = null, t.restoredBy = null
                 where t.board.id = :boardId and t.organization.id = :orgId and t.deletedAt is null
                """).setParameter("boardId", boardId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteColumnsOnBoard(UUID boardId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update BoardColumn c set c.deletedAt = :when, c.deletedBy = :by,
                    c.restoredAt = null, c.restoredBy = null
                 where c.board.id = :boardId and c.organization.id = :orgId and c.deletedAt is null
                """).setParameter("boardId", boardId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreColumnsOnBoard(UUID boardId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update BoardColumn c set c.deletedAt = null, c.deletedBy = null,
                    c.restoredAt = :when, c.restoredBy = :by
                 where c.board.id = :boardId and c.organization.id = :orgId and c.deletedAt is not null
                """).setParameter("boardId", boardId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreTasksOnBoard(UUID boardId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Task t set t.deletedAt = null, t.deletedBy = null,
                    t.restoredAt = :when, t.restoredBy = :by
                 where t.board.id = :boardId and t.organization.id = :orgId and t.deletedAt is not null
                """).setParameter("boardId", boardId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreCommentsOnBoard(UUID boardId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Comment c set c.deletedAt = null, c.deletedBy = null,
                    c.restoredAt = :when, c.restoredBy = :by
                 where c.task.board.id = :boardId and c.organization.id = :orgId and c.deletedAt is not null
                """).setParameter("boardId", boardId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteCommentsInColumn(UUID columnId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Comment c set c.deletedAt = :when, c.deletedBy = :by,
                    c.restoredAt = null, c.restoredBy = null
                 where c.task.column.id = :columnId and c.organization.id = :orgId and c.deletedAt is null
                """).setParameter("columnId", columnId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteTasksInColumn(UUID columnId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Task t set t.deletedAt = :when, t.deletedBy = :by,
                    t.restoredAt = null, t.restoredBy = null
                 where t.column.id = :columnId and t.organization.id = :orgId and t.deletedAt is null
                """).setParameter("columnId", columnId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreTasksInColumn(UUID columnId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Task t set t.deletedAt = null, t.deletedBy = null,
                    t.restoredAt = :when, t.restoredBy = :by
                 where t.column.id = :columnId and t.organization.id = :orgId and t.deletedAt is not null
                """).setParameter("columnId", columnId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreCommentsInColumn(UUID columnId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Comment c set c.deletedAt = null, c.deletedBy = null,
                    c.restoredAt = :when, c.restoredBy = :by
                 where c.task.column.id = :columnId and c.organization.id = :orgId and c.deletedAt is not null
                """).setParameter("columnId", columnId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void softDeleteCommentsOnTask(UUID taskId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Comment c set c.deletedAt = :when, c.deletedBy = :by,
                    c.restoredAt = null, c.restoredBy = null
                 where c.task.id = :taskId and c.organization.id = :orgId and c.deletedAt is null
                """).setParameter("taskId", taskId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }

    private void restoreCommentsOnTask(UUID taskId, UUID orgId, UUID by, Instant when) {
        em.createQuery("""
                update Comment c set c.deletedAt = null, c.deletedBy = null,
                    c.restoredAt = :when, c.restoredBy = :by
                 where c.task.id = :taskId and c.organization.id = :orgId and c.deletedAt is not null
                """).setParameter("taskId", taskId).setParameter("orgId", orgId)
                .setParameter("when", when).setParameter("by", by).executeUpdate();
    }
}
