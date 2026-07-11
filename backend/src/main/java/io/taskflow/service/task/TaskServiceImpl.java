package io.taskflow.service.task;

import io.taskflow.common.order.PositionCalculator;
import io.taskflow.common.tenant.TenantContext;
import io.taskflow.domain.Board;
import io.taskflow.domain.BoardColumn;
import io.taskflow.domain.Organization;
import io.taskflow.domain.Task;
import io.taskflow.domain.User;
import io.taskflow.domain.enums.ActivityType;
import io.taskflow.domain.enums.TaskPriority;
import io.taskflow.dto.task.CreateTaskRequest;
import io.taskflow.dto.task.MoveTaskRequest;
import io.taskflow.dto.task.TaskResponse;
import io.taskflow.dto.task.UpdateTaskRequest;
import io.taskflow.exception.BadRequestException;
import io.taskflow.exception.ConflictException;
import io.taskflow.exception.NotFoundException;
import io.taskflow.repository.BoardColumnRepository;
import io.taskflow.repository.BoardRepository;
import io.taskflow.repository.TaskRepository;
import io.taskflow.repository.UserRepository;
import io.taskflow.service.activity.ActivityPublisher;
import io.taskflow.service.mapping.DomainMappers;
import io.taskflow.service.trash.SoftDeleteCascadeService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository tasks;
    private final BoardRepository boards;
    private final BoardColumnRepository columns;
    private final UserRepository users;
    private final ActivityPublisher activity;
    private final EntityManager entityManager;
    private final SoftDeleteCascadeService softDeleteCascade;

    // -------------------------------------------------------------- create

    @Override
    @Transactional
    public TaskResponse create(UUID boardId, CreateTaskRequest request) {
        UUID orgId = TenantContext.requireOrganizationId();

        Board board = boards.findActiveByIdAndOrg(boardId, orgId)
                .orElseThrow(() -> new NotFoundException("Board", boardId));

        BoardColumn column = columns.findByIdAndOrg(request.columnId(), orgId)
                .orElseThrow(() -> new NotFoundException("Column", request.columnId()));
        if (!column.getBoard().getId().equals(boardId)) {
            throw new BadRequestException("COLUMN_NOT_IN_BOARD",
                    "Column does not belong to the target board");
        }

        long maxPos = tasks.maxPositionInColumn(column.getId());
        long position = maxPos == 0 ? PositionCalculator.first() : PositionCalculator.after(maxPos);

        User assignee = resolveAssignee(orgId, request.assigneeId());

        Task task = Task.builder()
                .organization(entityManager.getReference(Organization.class, orgId))
                .board(board)
                .column(column)
                .title(request.title())
                .description(request.description())
                .priority(request.priority() == null ? TaskPriority.MEDIUM : request.priority())
                .position(position)
                .assignee(assignee)
                .dueDate(request.dueDate())
                .build();
        task = tasks.save(task);

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", task.getTitle());
        payload.put("columnId", column.getId());
        if (assignee != null) payload.put("assigneeId", assignee.getId());
        activity.publish(ActivityType.TASK_CREATED,
                board.getProject().getId(), board.getId(), task.getId(), payload);

        return DomainMappers.toTaskResponse(task);
    }

    // ----------------------------------------------------------------- get

    @Override
    @Transactional(readOnly = true)
    public TaskResponse get(UUID taskId) {
        return DomainMappers.toTaskResponse(loadActive(taskId));
    }

    // -------------------------------------------------------------- update

    @Override
    @Transactional
    public TaskResponse update(UUID taskId, UpdateTaskRequest request) {
        Task task = loadActive(taskId);
        assertVersion(task.getVersion(), request.expectedVersion(), "Task");

        boolean assigneeChanged = false;
        UUID previousAssigneeId = task.getAssignee() == null ? null : task.getAssignee().getId();

        if (request.title() != null)       task.setTitle(request.title());
        if (request.description() != null) task.setDescription(request.description());
        if (request.priority() != null)    task.setPriority(request.priority());

        // Optional<T> contract: null = "leave alone", empty Optional = "clear".
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate().orElse(null));
        }
        if (request.assigneeId() != null) {
            UUID newAssigneeId = request.assigneeId().orElse(null);
            if (!Objects.equals(previousAssigneeId, newAssigneeId)) {
                task.setAssignee(resolveAssignee(TenantContext.requireOrganizationId(), newAssigneeId));
                assigneeChanged = true;
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", task.getTitle());
        activity.publish(ActivityType.TASK_UPDATED,
                task.getBoard().getProject().getId(), task.getBoard().getId(), task.getId(), payload);

        if (assigneeChanged) {
            UUID newAssigneeId = task.getAssignee() == null ? null : task.getAssignee().getId();
            ActivityType type = newAssigneeId == null
                    ? ActivityType.TASK_UNASSIGNED
                    : ActivityType.TASK_ASSIGNED;
            Map<String, Object> p = new HashMap<>();
            if (newAssigneeId != null) p.put("assigneeId", newAssigneeId.toString());
            if (previousAssigneeId != null) p.put("previousAssigneeId", previousAssigneeId.toString());
            activity.publish(type,
                    task.getBoard().getProject().getId(), task.getBoard().getId(), task.getId(), p);
        }

        return DomainMappers.toTaskResponse(task);
    }

    // ------------------------------------------------------------------ move

    @Override
    @Transactional
    public TaskResponse move(UUID taskId, MoveTaskRequest request) {
        UUID orgId = TenantContext.requireOrganizationId();
        Task task = loadActive(taskId);
        assertVersion(task.getVersion(), request.expectedVersion(), "Task");

        BoardColumn target = columns.findByIdAndOrg(request.targetColumnId(), orgId)
                .orElseThrow(() -> new NotFoundException("Column", request.targetColumnId()));

        if (!target.getBoard().getId().equals(task.getBoard().getId())) {
            // Cross-board moves are intentionally disallowed; would need a project-level move op.
            throw new BadRequestException("CROSS_BOARD_MOVE",
                    "Task cannot be moved to a column in a different board");
        }

        // Resolve neighbor positions; neighbors MUST be in the target column.
        Long beforePos = resolveNeighborPosition(request.beforeTaskId(), target.getId(), orgId, "beforeTaskId");
        Long afterPos  = resolveNeighborPosition(request.afterTaskId(),  target.getId(), orgId, "afterTaskId");

        long newPosition = computePosition(beforePos, afterPos, target.getId());

        UUID oldColumnId = task.getColumn().getId();
        task.setColumn(target);
        task.setPosition(newPosition);

        Map<String, Object> payload = new HashMap<>();
        payload.put("fromColumnId", oldColumnId);
        payload.put("toColumnId", target.getId());
        payload.put("position", newPosition);
        activity.publish(ActivityType.TASK_MOVED,
                task.getBoard().getProject().getId(), task.getBoard().getId(), task.getId(), payload);

        return DomainMappers.toTaskResponse(task);
    }

    // ---------------------------------------------------------------- delete

    @Override
    @Transactional
    public void delete(UUID taskId) {
        UUID orgId = TenantContext.requireOrganizationId();
        Task task = loadActive(taskId);
        softDeleteCascade.softDeleteTask(taskId, orgId, TenantContext.requireUserId(), Instant.now());
        activity.publish(ActivityType.TASK_DELETED,
                task.getBoard().getProject().getId(), task.getBoard().getId(), task.getId(),
                Map.of("title", task.getTitle()));
    }

    // --------------------------------------------------------------- helpers

    private Task loadActive(UUID taskId) {
        return tasks.findActiveByIdAndOrg(taskId, TenantContext.requireOrganizationId())
                .orElseThrow(() -> new NotFoundException("Task", taskId));
    }

    private User resolveAssignee(UUID orgId, UUID assigneeId) {
        if (assigneeId == null) return null;
        // Assignee must be a real user; we do not verify org membership here (that's a
        // separate concern handled at invitation time). A more strict version would
        // join through OrganizationMembership.
        return users.findById(assigneeId)
                .orElseThrow(() -> new BadRequestException("INVALID_ASSIGNEE",
                        "Assignee user not found"));
    }

    private Long resolveNeighborPosition(UUID neighborId, UUID targetColumnId,
                                         UUID orgId, String fieldName) {
        if (neighborId == null) return null;
        Task neighbor = tasks.findActiveByIdAndOrg(neighborId, orgId)
                .orElseThrow(() -> new BadRequestException("INVALID_NEIGHBOR",
                        fieldName + " not found"));
        if (!neighbor.getColumn().getId().equals(targetColumnId)) {
            throw new BadRequestException("INVALID_NEIGHBOR",
                    fieldName + " is not in the target column");
        }
        return neighbor.getPosition();
    }

    private long computePosition(Long beforePos, Long afterPos, UUID targetColumnId) {
        if (beforePos == null && afterPos == null) {
            // Appending to an arbitrary column.
            long maxPos = tasks.maxPositionInColumn(targetColumnId);
            return maxPos == 0 ? PositionCalculator.first() : PositionCalculator.after(maxPos);
        }
        if (beforePos == null) return PositionCalculator.before(afterPos);
        if (afterPos == null)  return PositionCalculator.after(beforePos);
        return PositionCalculator.between(beforePos, afterPos);
    }

    private static void assertVersion(long actual, Long expected, String resource) {
        if (expected == null) {
            throw new BadRequestException("VERSION_REQUIRED",
                    "expectedVersion is required for mutations on " + resource);
        }
        if (actual != expected) {
            throw new ConflictException("STALE_RESOURCE",
                    "The " + resource + " was modified by another user. Please refresh and retry.");
        }
    }
}
