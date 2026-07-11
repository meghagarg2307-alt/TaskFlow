package io.taskflow.service.comment;

import io.taskflow.dto.comment.CommentResponse;
import io.taskflow.dto.comment.CreateCommentRequest;

import java.util.List;
import java.util.UUID;

public interface CommentService {
    List<CommentResponse> list(UUID taskId);
    CommentResponse create(UUID taskId, CreateCommentRequest request);
    void delete(UUID commentId);
}
