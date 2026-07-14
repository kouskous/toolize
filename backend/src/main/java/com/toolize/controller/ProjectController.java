package com.toolize.controller;

import com.toolize.domain.ApiAuthConfig;
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

    public record ImportRequest(@NotBlank String name, String openApiUrl, ApiAuthConfig auth) {
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
            ApiProject project = projectService.importFromUrl(request.name(), request.openApiUrl(), request.auth());
            return ResponseEntity.ok(project);
        } catch (Exception ex) {
            return toErrorResponse(ex);
        }
    }

    @PostMapping(value = "/import-file", consumes = "multipart/form-data")
    public ResponseEntity<?> importFromFile(@RequestPart("name") String name,
                                             @RequestPart("file") MultipartFile file,
                                             @RequestPart(value = "authType", required = false) String authType,
                                             @RequestPart(value = "apiKeyName", required = false) String apiKeyName,
                                             @RequestPart(value = "apiKeyLocation", required = false) String apiKeyLocation,
                                             @RequestPart(value = "apiKeyValue", required = false) String apiKeyValue,
                                             @RequestPart(value = "bearerToken", required = false) String bearerToken,
                                             @RequestPart(value = "basicUsername", required = false) String basicUsername,
                                             @RequestPart(value = "basicPassword", required = false) String basicPassword) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            ApiAuthConfig auth = new ApiAuthConfig();
            if (authType != null && !authType.isBlank()) {
                auth.setType(ApiAuthConfig.AuthType.valueOf(authType));
            }
            auth.setApiKeyName(apiKeyName);
            if (apiKeyLocation != null && !apiKeyLocation.isBlank()) {
                auth.setApiKeyLocation(ApiAuthConfig.ApiKeyLocation.valueOf(apiKeyLocation));
            }
            auth.setApiKeyValue(apiKeyValue);
            auth.setBearerToken(bearerToken);
            auth.setBasicUsername(basicUsername);
            auth.setBasicPassword(basicPassword);

            ApiProject project = projectService.importFromContent(name, content, auth);
            return ResponseEntity.ok(project);
        } catch (IOException e) {
            return toErrorResponse(new IllegalArgumentException("Could not read uploaded file: " + e.getMessage()));
        } catch (Exception ex) {
            return toErrorResponse(ex);
        }
    }

    @PutMapping("/{id}/auth")
    public ResponseEntity<?> updateAuth(@PathVariable String id, @RequestBody ApiAuthConfig auth) {
        try {
            return projectService.updateAuth(id, auth)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
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
