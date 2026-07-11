package io.taskflow.controller;

import io.taskflow.common.tenant.TenantContext;
import io.taskflow.domain.ActivityLog;
import io.taskflow.dto.activity.ActivityResponse;
import io.taskflow.dto.common.UserRef;
import io.taskflow.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only activity feed. Always tenant-scoped via {@link TenantContext}.
 * Paginated (DB index supports descending order by {@code created_at}).
 */
@RestController
@RequiredArgsConstructor
public class ActivityController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ActivityLogRepository repository;

    @GetMapping("/activity")
    public Page<ActivityResponse> feed(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return repository.findFeed(
                TenantContext.requireOrganizationId(),
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(ActivityController::toResponse);
    }

    @GetMapping("/boards/{boardId}/activity")
    public Page<ActivityResponse> boardFeed(@PathVariable UUID boardId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return repository.findBoardFeed(
                TenantContext.requireOrganizationId(),
                boardId,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(ActivityController::toResponse);
    }

    private static ActivityResponse toResponse(ActivityLog a) {
        UserRef actor = a.getActor() == null ? null
                : new UserRef(a.getActor().getId(), a.getActor().getFullName(), a.getActor().getAvatarUrl());
        return new ActivityResponse(
                a.getId(), a.getActivityType(), actor,
                a.getProjectId(), a.getBoardId(), a.getTaskId(),
                a.getPayload(), a.getCreatedAt());
    }
}
