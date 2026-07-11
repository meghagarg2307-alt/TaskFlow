package io.taskflow.controller;

import io.taskflow.dto.comment.CommentResponse;
import io.taskflow.dto.comment.CreateCommentRequest;
import io.taskflow.service.comment.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/tasks/{taskId}/comments")
    public List<CommentResponse> list(@PathVariable UUID taskId) {
        return commentService.list(taskId);
    }

    @PostMapping("/tasks/{taskId}/comments")
    @PreAuthorize("hasRole('MEMBER')")
    public CommentResponse create(@PathVariable UUID taskId,
                                  @Valid @RequestBody CreateCommentRequest req) {
        return commentService.create(taskId, req);
    }

    @DeleteMapping("/comments/{commentId}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<Void> delete(@PathVariable UUID commentId) {
        commentService.delete(commentId);
        return ResponseEntity.noContent().build();
    }
}
