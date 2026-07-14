package com.toolize.controller;

import com.toolize.domain.ApiProject;
import com.toolize.service.ProjectPersistenceService;
import com.toolize.service.ProjectService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectPersistenceService persistenceService;

    public ProjectController(ProjectService projectService, ProjectPersistenceService persistenceService) {
        this.projectService = projectService;
        this.persistenceService = persistenceService;
    }

    public record ImportRequest(@NotBlank String name, String openApiUrl) {
    }

    @GetMapping
    public List<ApiProject> listProjects() {
        return persistenceService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiProject> getProject(@PathVariable String id) {
        return persistenceService.find(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/import")
    public ResponseEntity<?> importProject(@RequestBody ImportRequest request) {
        try {
            if (request.openApiUrl() == null || request.openApiUrl().isBlank()) {
                throw new IllegalArgumentException("openApiUrl is required");
            }
            ApiProject project = projectService.importFromUrl(request.name(), request.openApiUrl());
            return ResponseEntity.ok(project);
        } catch (Exception ex) {
            return toErrorResponse(ex);
        }
    }

    @PostMapping(value = "/import-file", consumes = "multipart/form-data")
    public ResponseEntity<?> importFromFile(@RequestPart("name") String name,
                                             @RequestPart("file") MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            ApiProject project = projectService.importFromContent(name, content);
            return ResponseEntity.ok(project);
        } catch (IOException e) {
            return toErrorResponse(new IllegalArgumentException("Could not read uploaded file: " + e.getMessage()));
        } catch (Exception ex) {
            return toErrorResponse(ex);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable String id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<?> toErrorResponse(Throwable ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody(ex.getMessage()));
    }

    public record ErrorBody(String error) {
    }
}
