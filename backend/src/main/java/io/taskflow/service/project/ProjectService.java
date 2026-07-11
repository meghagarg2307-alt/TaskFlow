package io.taskflow.service.project;

import io.taskflow.dto.project.CreateProjectRequest;
import io.taskflow.dto.project.ProjectResponse;
import io.taskflow.dto.project.UpdateProjectRequest;

import java.util.List;
import java.util.UUID;

public interface ProjectService {
    ProjectResponse create(CreateProjectRequest request);
    List<ProjectResponse> listAll();
    ProjectResponse get(UUID id);
    ProjectResponse update(UUID id, UpdateProjectRequest request);
    void delete(UUID id);
}
