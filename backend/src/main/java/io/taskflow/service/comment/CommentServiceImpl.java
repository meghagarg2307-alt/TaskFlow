package io.taskflow.service.comment;

import io.taskflow.common.tenant.TenantContext;
import io.taskflow.domain.Comment;
import io.taskflow.domain.Organization;
import io.taskflow.domain.Task;
import io.taskflow.domain.User;
import io.taskflow.domain.enums.ActivityType;
import io.taskflow.dto.comment.CommentResponse;
import io.taskflow.dto.comment.CreateCommentRequest;
import io.taskflow.exception.ForbiddenException;
import io.taskflow.exception.NotFoundException;
import io.taskflow.common.softdelete.SoftDeleteSupport;
import io.taskflow.repository.CommentRepository;
import io.taskflow.repository.TaskRepository;
import io.taskflow.service.activity.ActivityPublisher;
import io.taskflow.service.mapping.DomainMappers;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository comments;
    private final TaskRepository tasks;
    private final ActivityPublisher activity;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> list(UUID taskId) {
        UUID orgId = TenantContext.requireOrganizationId();
        tasks.findActiveByIdAndOrg(taskId, orgId)
                .orElseThrow(() -> new NotFoundException("Task", taskId));
        return comments.findActiveByTask(taskId, orgId).stream()
                .map(DomainMappers::toCommentResponse).toList();
    }

    @Override
    @Transactional
    public CommentResponse create(UUID taskId, CreateCommentRequest request) {
        UUID orgId = TenantContext.requireOrganizationId();
        UUID userId = TenantContext.requireUserId();
        Task task = tasks.findActiveByIdAndOrg(taskId, orgId)
                .orElseThrow(() -> new NotFoundException("Task", taskId));

        Comment comment = Comment.builder()
                .organization(entityManager.getReference(Organization.class, orgId))
                .task(task)
                .author(entityManager.getReference(User.class, userId))
                .body(request.body())
                .build();
        comment = comments.save(comment);

        activity.publish(ActivityType.COMMENT_ADDED,
                task.getBoard().getProject().getId(),
                task.getBoard().getId(),
                task.getId(),
                Map.of("commentId", comment.getId()));

        return DomainMappers.toCommentResponse(comment);
    }

    @Override
    @Transactional
    public void delete(UUID commentId) {
        UUID orgId  = TenantContext.requireOrganizationId();
        UUID userId = TenantContext.requireUserId();
        Comment comment = comments.findActiveByIdAndOrg(commentId, orgId)
                .orElseThrow(() -> new NotFoundException("Comment", commentId));

        // Authors can delete their own comments. Managers/Admins can delete anyone's.
        boolean isAuthor = comment.getAuthor().getId().equals(userId);
        if (!isAuthor && !TenantContext.get().isAtLeastManager()) {
            throw new ForbiddenException("You can only delete your own comments");
        }

        SoftDeleteSupport.markDeleted(comment, userId);
        activity.publish(ActivityType.COMMENT_DELETED,
                comment.getTask().getBoard().getProject().getId(),
                comment.getTask().getBoard().getId(),
                comment.getTask().getId(),
                Map.of("commentId", comment.getId()));
    }
}
