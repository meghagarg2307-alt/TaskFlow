package io.taskflow.service.task;

import io.taskflow.dto.task.CreateTaskRequest;
import io.taskflow.dto.task.MoveTaskRequest;
import io.taskflow.dto.task.TaskResponse;
import io.taskflow.dto.task.UpdateTaskRequest;

import java.util.UUID;

public interface TaskService {

    TaskResponse create(UUID boardId, CreateTaskRequest request);

    TaskResponse get(UUID taskId);

    TaskResponse update(UUID taskId, UpdateTaskRequest request);

    /**
     * Move a task to a new column / position. This is the canonical drag-drop write.
     * Enforces optimistic locking via {@code request.expectedVersion} so two
     * concurrent drags don't fight silently.
     */
    TaskResponse move(UUID taskId, MoveTaskRequest request);

    void delete(UUID taskId);
}
