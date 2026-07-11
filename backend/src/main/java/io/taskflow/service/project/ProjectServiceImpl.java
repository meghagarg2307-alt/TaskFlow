package io.taskflow.service.project;

import io.taskflow.common.tenant.TenantContext;
import io.taskflow.domain.Organization;
import io.taskflow.domain.Project;
import io.taskflow.domain.enums.ActivityType;
import io.taskflow.dto.project.CreateProjectRequest;
import io.taskflow.dto.project.ProjectResponse;
import io.taskflow.dto.project.UpdateProjectRequest;
import io.taskflow.exception.ConflictException;
import io.taskflow.exception.NotFoundException;
import io.taskflow.repository.ProjectRepository;
import io.taskflow.service.activity.ActivityPublisher;
import io.taskflow.service.mapping.DomainMappers;
import io.taskflow.service.trash.SoftDeleteCascadeService;
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
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository repository;
    private final ActivityPublisher activity;
    private final EntityManager entityManager;
    private final SoftDeleteCascadeService softDeleteCascade;

    @Override
    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        UUID orgId = TenantContext.requireOrganizationId();
        if (repository.existsByOrganization_IdAndKeyIgnoreCase(orgId, request.key())) {
            throw new ConflictException("PROJECT_KEY_TAKEN",
                    "A project with key '" + request.key() + "' already exists");
        }
        Project project = Project.builder()
                .organization(entityManager.getReference(Organization.class, orgId))
                .name(request.name())
                .key(request.key().toUpperCase())
                .description(request.description())
                .build();
        project = repository.save(project);

        activity.publish(ActivityType.PROJECT_CREATED, project.getId(), null, null,
                Map.of("name", project.getName(), "key", project.getKey()));
        return DomainMappers.toProjectResponse(project);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> listAll() {
        return repository.findAllActive(TenantContext.requireOrganizationId())
                .stream().map(DomainMappers::toProjectResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse get(UUID id) {
        return DomainMappers.toProjectResponse(loadActive(id));
    }

    @Override
    @Transactional
    public ProjectResponse update(UUID id, UpdateProjectRequest request) {
        Project project = loadActive(id);
        if (request.name() != null) project.setName(request.name());
        if (request.description() != null) project.setDescription(request.description());

        activity.publish(ActivityType.PROJECT_UPDATED, project.getId(), null, null,
                Map.of("name", project.getName()));
        return DomainMappers.toProjectResponse(project);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        UUID orgId = TenantContext.requireOrganizationId();
        Project project = loadActive(id);
        Instant when = Instant.now();
        UUID deletedBy = TenantContext.requireUserId();
        softDeleteCascade.softDeleteProject(project.getId(), orgId, deletedBy, when);
        activity.publish(ActivityType.PROJECT_DELETED, project.getId(), null, null,
                Map.of("name", project.getName()));
    }

    private Project loadActive(UUID id) {
        return repository.findActiveByIdAndOrg(id, TenantContext.requireOrganizationId())
                .orElseThrow(() -> new NotFoundException("Project", id));
    }
}
