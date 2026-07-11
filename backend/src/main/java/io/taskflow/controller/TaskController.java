package io.taskflow.controller;

import io.taskflow.dto.task.CreateTaskRequest;
import io.taskflow.dto.task.MoveTaskRequest;
import io.taskflow.dto.task.TaskResponse;
import io.taskflow.dto.task.UpdateTaskRequest;
import io.taskflow.service.task.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping("/boards/{boardId}/tasks")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<TaskResponse> create(@PathVariable UUID boardId,
                                               @Valid @RequestBody CreateTaskRequest req) {
        TaskResponse created = taskService.create(boardId, req);
        return ResponseEntity.created(URI.create("/tasks/" + created.id())).body(created);
    }

    @GetMapping("/tasks/{taskId}")
    public TaskResponse get(@PathVariable UUID taskId) {
        return taskService.get(taskId);
    }

    @PatchMapping("/tasks/{taskId}")
    @PreAuthorize("hasRole('MEMBER')")
    public TaskResponse update(@PathVariable UUID taskId,
                               @Valid @RequestBody UpdateTaskRequest req) {
        return taskService.update(taskId, req);
    }

    /**
     * Drag-drop endpoint. Separate from PATCH /tasks/{id} because the semantics
     * (and concurrency story) are different: a move is a single atomic op with two
     * neighbor refs, while PATCH is a field-by-field edit.
     */
    @PostMapping("/tasks/{taskId}/move")
    @PreAuthorize("hasRole('MEMBER')")
    public TaskResponse move(@PathVariable UUID taskId,
                             @Valid @RequestBody MoveTaskRequest req) {
        return taskService.move(taskId, req);
    }

    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<Void> delete(@PathVariable UUID taskId) {
        taskService.delete(taskId);
        return ResponseEntity.noContent().build();
    }
}
