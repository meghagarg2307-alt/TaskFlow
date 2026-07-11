package io.taskflow.service.mapping;

import io.taskflow.domain.Board;
import io.taskflow.domain.BoardColumn;
import io.taskflow.domain.Comment;
import io.taskflow.domain.Project;
import io.taskflow.domain.Task;
import io.taskflow.domain.User;
import io.taskflow.dto.board.BoardSnapshot;
import io.taskflow.dto.board.BoardSummary;
import io.taskflow.dto.comment.CommentResponse;
import io.taskflow.dto.common.UserRef;
import io.taskflow.dto.project.ProjectResponse;
import io.taskflow.dto.task.TaskResponse;

import java.util.List;

/**
 * Hand-written entity → DTO mappers, gathered in one place.
 *
 * <p>We avoided MapStruct here on purpose for readability — each mapping is small and
 * the rules (e.g. denormalize {@code columnId} onto task DTO) are explicit. MapStruct
 * is still on the classpath and is the right choice for the larger flows in Step 4.</p>
 */
public final class DomainMappers {

    private DomainMappers() {}

    public static UserRef toRef(User u) {
        if (u == null) return null;
        return new UserRef(u.getId(), u.getFullName(), u.getAvatarUrl());
    }

    public static ProjectResponse toProjectResponse(Project p) {
        return new ProjectResponse(
                p.getId(), p.getName(), p.getKey(), p.getDescription(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    public static BoardSummary toBoardSummary(Board b) {
        return new BoardSummary(
                b.getId(), b.getProject().getId(),
                b.getName(), b.getDescription(),
                b.getVersion(), b.getCreatedAt(), b.getUpdatedAt());
    }

    public static BoardSnapshot toBoardSnapshot(Board b,
                                                List<BoardColumn> columns,
                                                List<Task> tasks) {
        List<BoardSnapshot.ColumnView> columnViews = columns.stream()
                .map(c -> new BoardSnapshot.ColumnView(
                        c.getId(), c.getName(), c.getPosition(), c.getWipLimit()))
                .toList();
        List<TaskResponse> taskResponses = tasks.stream()
                .map(DomainMappers::toTaskResponse)
                .toList();
        return new BoardSnapshot(
                b.getId(), b.getProject().getId(), b.getName(), b.getDescription(),
                b.getVersion(), b.getUpdatedAt(),
                columnViews, taskResponses);
    }

    public static TaskResponse toTaskResponse(Task t) {
        return new TaskResponse(
                t.getId(),
                t.getBoard().getId(),
                t.getColumn().getId(),
                t.getTitle(),
                t.getDescription(),
                t.getPriority(),
                t.getPosition(),
                toRef(t.getAssignee()),
                t.getDueDate(),
                t.getVersion(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    public static CommentResponse toCommentResponse(Comment c) {
        return new CommentResponse(
                c.getId(),
                c.getTask().getId(),
                toRef(c.getAuthor()),
                c.getBody(),
                c.getCreatedAt());
    }
}
