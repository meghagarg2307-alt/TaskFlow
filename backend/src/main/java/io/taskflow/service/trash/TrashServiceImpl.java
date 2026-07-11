package io.taskflow.service.trash;

import io.taskflow.common.tenant.TenantContext;
import io.taskflow.config.TrashProperties;
import io.taskflow.domain.Board;
import io.taskflow.domain.Organization;
import io.taskflow.domain.Project;
import io.taskflow.domain.Task;
import io.taskflow.domain.User;
import io.taskflow.domain.enums.ActivityType;
import io.taskflow.domain.enums.TrashResourceType;
import io.taskflow.dto.trash.TrashItemResponse;
import io.taskflow.exception.ForbiddenException;
import io.taskflow.exception.NotFoundException;
import io.taskflow.repository.BoardRepository;
import io.taskflow.repository.OrganizationRepository;
import io.taskflow.repository.ProjectRepository;
import io.taskflow.repository.TaskRepository;
import io.taskflow.repository.UserRepository;
import io.taskflow.service.activity.ActivityPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrashServiceImpl implements TrashService {

    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final SoftDeleteCascadeService cascade;
    private final ActivityPublisher activity;
    private final TrashProperties trashProperties;

    @Override
    @Transactional(readOnly = true)
    public Page<TrashItemResponse> listTrash(TrashResourceType type, int page, int size) {
        UUID orgId = TenantContext.requireOrganizationId();
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<TrashItemResponse> all = new ArrayList<>();

        if (type == null || type == TrashResourceType.WORKSPACE) {
            organizationRepository.findById(orgId)
                    .filter(Organization::isDeleted)
                    .ifPresent(org -> all.add(toWorkspaceItem(org)));
        }
        if (type == null || type == TrashResourceType.PROJECT) {
            projectRepository.findDeletedRoots(orgId).forEach(p -> all.add(toProjectItem(p)));
        }
        if (type == null || type == TrashResourceType.BOARD) {
            boardRepository.findDeletedRoots(orgId).forEach(b -> all.add(toBoardItem(b)));
        }
        if (type == null || type == TrashResourceType.TASK) {
            taskRepository.findDeletedRoots(orgId).forEach(t -> all.add(toTaskItem(t)));
        }

        all.sort(Comparator.comparing(TrashItemResponse::deletedAt).reversed());
        Map<UUID, String> names = resolveDeleterNames(all);

        List<TrashItemResponse> enriched = all.stream()
                .map(item -> new TrashItemResponse(
                        item.resourceType(),
                        item.id(),
                        item.name(),
                        item.description(),
                        item.deletedAt(),
                        item.deletedBy(),
                        item.deletedBy() == null ? null : names.get(item.deletedBy()),
                        item.daysUntilPermanentDeletion(),
                        item.parentId(),
                        item.parentName()))
                .toList();

        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, enriched.size());
        int to = Math.min(from + safeSize, enriched.size());
        return new PageImpl<>(enriched.subList(from, to), PageRequest.of(safePage, safeSize), enriched.size());
    }

    @Override
    @Transactional
    public void restore(TrashResourceType type, UUID id) {
        requireAdmin();
        UUID orgId = TenantContext.requireOrganizationId();
        UUID userId = TenantContext.requireUserId();
        Instant when = Instant.now();

        switch (type) {
            case WORKSPACE -> {
                Organization org = loadDeletedOrg(orgId);
                cascade.restoreOrganization(org.getId(), userId, when);
                activity.publish(ActivityType.ORGANIZATION_RESTORED, null, null, null,
                        Map.of("organizationId", org.getId(), "name", org.getName()));
            }
            case PROJECT -> {
                Project p = projectRepository.findDeletedByIdAndOrg(id, orgId)
                        .orElseThrow(() -> new NotFoundException("Project", id));
                cascade.restoreProject(p.getId(), orgId, userId, when);
                activity.publish(ActivityType.PROJECT_RESTORED, p.getId(), null, null,
                        Map.of("name", p.getName()));
            }
            case BOARD -> {
                Board b = boardRepository.findDeletedByIdAndOrg(id, orgId)
                        .orElseThrow(() -> new NotFoundException("Board", id));
                cascade.restoreBoard(b.getId(), orgId, userId, when);
                activity.publish(ActivityType.BOARD_RESTORED, b.getProject().getId(), b.getId(), null,
                        Map.of("name", b.getName()));
            }
            case TASK -> {
                Task t = taskRepository.findDeletedByIdAndOrg(id, orgId)
                        .orElseThrow(() -> new NotFoundException("Task", id));
                cascade.restoreTask(t.getId(), orgId, userId, when);
                activity.publish(ActivityType.TASK_RESTORED,
                        t.getBoard().getProject().getId(), t.getBoard().getId(), t.getId(),
                        Map.of("title", t.getTitle()));
            }
        }
    }

    @Override
    @Transactional
    public void permanentlyDelete(TrashResourceType type, UUID id) {
        requireAdmin();
        UUID orgId = TenantContext.requireOrganizationId();

        switch (type) {
            case WORKSPACE -> {
                Organization org = loadDeletedOrg(orgId);
                activity.publish(ActivityType.ORGANIZATION_PERMANENTLY_DELETED, null, null, null,
                        Map.of("organizationId", org.getId(), "name", org.getName()));
                cascade.permanentlyDeleteOrganization(org.getId());
            }
            case PROJECT -> {
                Project p = projectRepository.findDeletedByIdAndOrg(id, orgId)
                        .orElseThrow(() -> new NotFoundException("Project", id));
                activity.publish(ActivityType.PROJECT_PERMANENTLY_DELETED, p.getId(), null, null,
                        Map.of("name", p.getName()));
                cascade.permanentlyDeleteProject(p.getId(), orgId);
            }
            case BOARD -> {
                Board b = boardRepository.findDeletedByIdAndOrg(id, orgId)
                        .orElseThrow(() -> new NotFoundException("Board", id));
                activity.publish(ActivityType.BOARD_PERMANENTLY_DELETED, b.getProject().getId(), b.getId(), null,
                        Map.of("name", b.getName()));
                cascade.permanentlyDeleteBoard(b.getId(), orgId);
            }
            case TASK -> {
                Task t = taskRepository.findDeletedByIdAndOrg(id, orgId)
                        .orElseThrow(() -> new NotFoundException("Task", id));
                activity.publish(ActivityType.TASK_PERMANENTLY_DELETED,
                        t.getBoard().getProject().getId(), t.getBoard().getId(), t.getId(),
                        Map.of("title", t.getTitle()));
                cascade.permanentlyDeleteTask(t.getId(), orgId);
            }
        }
    }

    private Organization loadDeletedOrg(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new NotFoundException("Organization", orgId));
        if (!org.isDeleted()) {
            throw new NotFoundException("Organization", orgId);
        }
        return org;
    }

    private void requireAdmin() {
        if (!TenantContext.get().isAdmin()) {
            throw new ForbiddenException("ADMIN_REQUIRED",
                    "Only organization administrators can restore or permanently delete items");
        }
    }

    private TrashItemResponse toWorkspaceItem(Organization org) {
        return new TrashItemResponse(
                TrashResourceType.WORKSPACE,
                org.getId(),
                org.getName(),
                org.getDescription(),
                org.getDeletedAt(),
                org.getDeletedBy(),
                null,
                daysUntilPurge(org.getDeletedAt()),
                null,
                null);
    }

    private TrashItemResponse toProjectItem(Project p) {
        return new TrashItemResponse(
                TrashResourceType.PROJECT,
                p.getId(),
                p.getName(),
                p.getKey(),
                p.getDeletedAt(),
                p.getDeletedBy(),
                null,
                daysUntilPurge(p.getDeletedAt()),
                null,
                null);
    }

    private TrashItemResponse toBoardItem(Board b) {
        return new TrashItemResponse(
                TrashResourceType.BOARD,
                b.getId(),
                b.getName(),
                b.getDescription(),
                b.getDeletedAt(),
                b.getDeletedBy(),
                null,
                daysUntilPurge(b.getDeletedAt()),
                b.getProject().getId(),
                b.getProject().getName());
    }

    private TrashItemResponse toTaskItem(Task t) {
        return new TrashItemResponse(
                TrashResourceType.TASK,
                t.getId(),
                t.getTitle(),
                t.getDescription(),
                t.getDeletedAt(),
                t.getDeletedBy(),
                null,
                daysUntilPurge(t.getDeletedAt()),
                t.getBoard().getId(),
                t.getBoard().getName());
    }

    private int daysUntilPurge(Instant deletedAt) {
        if (deletedAt == null) return trashProperties.retentionDays();
        Instant purgeAt = deletedAt.plus(trashProperties.retentionDays(), ChronoUnit.DAYS);
        long days = ChronoUnit.DAYS.between(Instant.now(), purgeAt);
        return (int) Math.max(0, days);
    }

    private Map<UUID, String> resolveDeleterNames(List<TrashItemResponse> items) {
        List<UUID> ids = items.stream()
                .map(TrashItemResponse::deletedBy)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return userRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getFullName));
    }
}
