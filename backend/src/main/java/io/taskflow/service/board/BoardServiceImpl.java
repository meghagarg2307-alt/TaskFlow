package io.taskflow.service.board;

import io.taskflow.common.order.PositionCalculator;
import io.taskflow.common.tenant.TenantContext;
import io.taskflow.domain.Board;
import io.taskflow.domain.BoardColumn;
import io.taskflow.domain.Organization;
import io.taskflow.domain.Project;
import io.taskflow.domain.Task;
import io.taskflow.domain.enums.ActivityType;
import io.taskflow.dto.board.BoardSnapshot;
import io.taskflow.dto.board.BoardSummary;
import io.taskflow.dto.board.CreateBoardRequest;
import io.taskflow.dto.board.CreateColumnRequest;
import io.taskflow.dto.board.UpdateBoardRequest;
import io.taskflow.dto.board.UpdateColumnRequest;
import io.taskflow.exception.BadRequestException;
import io.taskflow.exception.NotFoundException;
import io.taskflow.repository.BoardColumnRepository;
import io.taskflow.repository.BoardRepository;
import io.taskflow.repository.ProjectRepository;
import io.taskflow.repository.TaskRepository;
import io.taskflow.service.activity.ActivityPublisher;
import io.taskflow.service.mapping.DomainMappers;
import io.taskflow.service.trash.SoftDeleteCascadeService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ActivityPublisher activity;
    private final EntityManager entityManager;
    private final SoftDeleteCascadeService softDeleteCascade;

    // ---------------------------------------------------------------- boards

    @Override
    @Transactional
    public BoardSummary createBoard(UUID projectId, CreateBoardRequest request) {
        UUID orgId = TenantContext.requireOrganizationId();
        Project project = projectRepository.findActiveByIdAndOrg(projectId, orgId)
                .orElseThrow(() -> new NotFoundException("Project", projectId));

        Board board = Board.builder()
                .organization(entityManager.getReference(Organization.class, orgId))
                .project(project)
                .name(request.name())
                .description(request.description())
                .build();
        board = boardRepository.save(board);

        activity.publish(ActivityType.BOARD_CREATED, project.getId(), board.getId(), null,
                Map.of("name", board.getName()));
        return DomainMappers.toBoardSummary(board);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BoardSummary> listBoardsInProject(UUID projectId) {
        UUID orgId = TenantContext.requireOrganizationId();
        return boardRepository.findAllActiveInProject(projectId, orgId)
                .stream().map(DomainMappers::toBoardSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BoardSnapshot getBoardSnapshot(UUID boardId) {
        UUID orgId = TenantContext.requireOrganizationId();
        Board board = loadActiveBoard(boardId);
        List<BoardColumn> columns = columnRepository.findOrderedByBoard(boardId, orgId);
        List<Task> tasks = taskRepository.findBoardSnapshot(boardId, orgId);
        return DomainMappers.toBoardSnapshot(board, columns, tasks);
    }

    @Override
    @Transactional
    public BoardSummary updateBoard(UUID boardId, UpdateBoardRequest request) {
        Board board = loadActiveBoard(boardId);
        assertExpectedVersion(board.getVersion(), request.expectedVersion(), "Board");

        if (request.name() != null) board.setName(request.name());
        if (request.description() != null) board.setDescription(request.description());

        activity.publish(ActivityType.BOARD_UPDATED, board.getProject().getId(), board.getId(), null,
                Map.of("name", board.getName()));
        return DomainMappers.toBoardSummary(board);
    }

    @Override
    @Transactional
    public void deleteBoard(UUID boardId) {
        UUID orgId = TenantContext.requireOrganizationId();
        Board board = loadActiveBoard(boardId);
        softDeleteCascade.softDeleteBoard(boardId, orgId, TenantContext.requireUserId(), Instant.now());
        activity.publish(ActivityType.BOARD_DELETED, board.getProject().getId(), board.getId(), null,
                Map.of("name", board.getName()));
    }

    // --------------------------------------------------------------- columns

    @Override
    @Transactional
    public BoardSnapshot.ColumnView createColumn(UUID boardId, CreateColumnRequest request) {
        UUID orgId = TenantContext.requireOrganizationId();
        Board board = loadActiveBoard(boardId);
        long maxPos = columnRepository.maxPositionInBoard(boardId);
        long position = maxPos == 0 ? PositionCalculator.first() : PositionCalculator.after(maxPos);

        BoardColumn column = BoardColumn.builder()
                .organization(entityManager.getReference(Organization.class, orgId))
                .board(board)
                .name(request.name())
                .position(position)
                .wipLimit(request.wipLimit())
                .build();
        column = columnRepository.save(column);

        activity.publish(ActivityType.COLUMN_CREATED,
                board.getProject().getId(), board.getId(), null,
                Map.of("columnId", column.getId(), "name", column.getName()));
        return new BoardSnapshot.ColumnView(column.getId(), column.getName(),
                column.getPosition(), column.getWipLimit());
    }

    @Override
    @Transactional
    public BoardSnapshot.ColumnView updateColumn(UUID columnId, UpdateColumnRequest request) {
        UUID orgId = TenantContext.requireOrganizationId();
        BoardColumn column = columnRepository.findByIdAndOrg(columnId, orgId)
                .orElseThrow(() -> new NotFoundException("Column", columnId));

        if (request.name() != null) column.setName(request.name());
        if (request.wipLimit() != null) {
            // Treat 0 as "remove limit"; Postgres CHECK is positive integers.
            column.setWipLimit(request.wipLimit() == 0 ? null : request.wipLimit());
        }
        if (request.beforeColumnId() != null || request.afterColumnId() != null) {
            column.setPosition(computeColumnPosition(column, request, orgId));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("columnId", column.getId());
        payload.put("name", column.getName());
        activity.publish(ActivityType.COLUMN_UPDATED,
                column.getBoard().getProject().getId(), column.getBoard().getId(), null, payload);

        return new BoardSnapshot.ColumnView(column.getId(), column.getName(),
                column.getPosition(), column.getWipLimit());
    }

    @Override
    @Transactional
    public void deleteColumn(UUID columnId) {
        UUID orgId = TenantContext.requireOrganizationId();
        BoardColumn column = columnRepository.findByIdAndOrg(columnId, orgId)
                .orElseThrow(() -> new NotFoundException("Column", columnId));

        softDeleteCascade.softDeleteColumn(columnId, orgId, TenantContext.requireUserId(), Instant.now());
        activity.publish(ActivityType.COLUMN_DELETED,
                column.getBoard().getProject().getId(), column.getBoard().getId(), null,
                Map.of("columnId", column.getId(), "name", column.getName()));
    }

    // --------------------------------------------------------------- helpers

    private Board loadActiveBoard(UUID boardId) {
        return boardRepository.findActiveByIdAndOrg(boardId, TenantContext.requireOrganizationId())
                .orElseThrow(() -> new NotFoundException("Board", boardId));
    }

    private long computeColumnPosition(BoardColumn moving,
                                       UpdateColumnRequest request,
                                       UUID orgId) {
        // The two neighbors must be in the same board.
        Long beforePos = request.beforeColumnId() == null ? null
                : columnRepository.findByIdAndOrg(request.beforeColumnId(), orgId)
                    .map(BoardColumn::getPosition)
                    .orElseThrow(() -> new BadRequestException("INVALID_NEIGHBOR",
                            "beforeColumnId not found in this board"));
        Long afterPos = request.afterColumnId() == null ? null
                : columnRepository.findByIdAndOrg(request.afterColumnId(), orgId)
                    .map(BoardColumn::getPosition)
                    .orElseThrow(() -> new BadRequestException("INVALID_NEIGHBOR",
                            "afterColumnId not found in this board"));

        if (beforePos == null && afterPos != null)  return PositionCalculator.before(afterPos);
        if (afterPos == null && beforePos != null)  return PositionCalculator.after(beforePos);
        if (beforePos != null && afterPos != null)  return PositionCalculator.between(beforePos, afterPos);
        return moving.getPosition(); // No neighbors → keep position
    }

    private static void assertExpectedVersion(long actual, Long expected, String resource) {
        if (expected == null) {
            throw new BadRequestException("VERSION_REQUIRED",
                    "expectedVersion is required for mutations on " + resource);
        }
        if (actual != expected) {
            // Surface the same code as JPA's optimistic lock to keep the SPA branch simple.
            throw new ObjectOptimisticLockingFailureException(resource, expected);
        }
    }
}
