package io.taskflow.controller;

import io.taskflow.dto.project.CreateProjectRequest;
import io.taskflow.dto.project.ProjectResponse;
import io.taskflow.dto.project.UpdateProjectRequest;
import io.taskflow.service.project.ProjectService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")  // role hierarchy makes ADMIN implicit
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest req) {
        ProjectResponse created = projectService.create(req);
        return ResponseEntity.created(URI.create("/projects/" + created.id())).body(created);
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.listAll();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        return projectService.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ProjectResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest req) {
        return projectService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
