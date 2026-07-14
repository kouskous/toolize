package com.toolize.controller;

import com.toolize.domain.ApiProject;
import com.toolize.service.ProjectPersistenceService;
import com.toolize.service.ProjectService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    public Mono<ResponseEntity<?>> importProject(@RequestBody ImportRequest request) {
        return Mono.fromCallable(() -> {
                    if (request.openApiUrl() == null || request.openApiUrl().isBlank()) {
                        throw new IllegalArgumentException("openApiUrl is required");
                    }
                    return projectService.importFromUrl(request.name(), request.openApiUrl());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(project -> ResponseEntity.ok((Object) project))
                .onErrorResume(this::toErrorResponse);
    }

    @PostMapping(value = "/import-file", consumes = "multipart/form-data")
    public Mono<ResponseEntity<?>> importFromFile(@RequestPart("name") String name,
                                                   @RequestPart("file") MultipartFile file) {
        return Mono.fromCallable(() -> {
                    try {
                        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                        return projectService.importFromContent(name, content);
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(project -> ResponseEntity.ok((Object) project))
                .onErrorResume(this::toErrorResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable String id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    private Mono<ResponseEntity<?>> toErrorResponse(Throwable ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body((Object) new ErrorBody(ex.getMessage())));
    }

    public record ErrorBody(String error) {
    }
}
