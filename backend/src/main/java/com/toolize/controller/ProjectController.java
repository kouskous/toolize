package com.toolize.controller;

import com.toolize.domain.ApiAuthConfig;
import com.toolize.domain.ApiProject;
import com.toolize.domain.OpenApiOperation;
import com.toolize.service.ProjectPersistenceService;
import com.toolize.service.ProjectService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final JsonMapper JSON = new JsonMapper();

    private final ProjectService projectService;
    private final ProjectPersistenceService persistenceService;

    public ProjectController(ProjectService projectService, ProjectPersistenceService persistenceService) {
        this.projectService = projectService;
        this.persistenceService = persistenceService;
    }

    public record ImportRequest(@NotBlank String name, String openApiUrl, ApiAuthConfig auth,
                                 Set<String> enabledOperationIds) {
    }

    public record PreviewRequest(@NotBlank String openApiUrl) {
    }

    public record EndpointSummary(String operationId, String method, String path, String summary) {
    }

    public record EndpointsRequest(Set<String> enabledOperationIds) {
    }

    public record PreviewResponse(List<EndpointSummary> endpoints, ApiAuthConfig suggestedAuth) {
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

    @PostMapping("/preview")
    public ResponseEntity<?> previewFromUrl(@RequestBody PreviewRequest request) {
        try {
            ProjectService.PreviewResult result = projectService.previewFromUrl(request.openApiUrl());
            return ResponseEntity.ok(new PreviewResponse(toSummaries(result.operations()), result.suggestedAuth()));
        } catch (Exception ex) {
            return toErrorResponse(ex);
        }
    }

    @PostMapping(value = "/preview-file", consumes = "multipart/form-data")
    public ResponseEntity<?> previewFromFile(@RequestPart("file") MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            ProjectService.PreviewResult result = projectService.previewFromContent(content);
            return ResponseEntity.ok(new PreviewResponse(toSummaries(result.operations()), result.suggestedAuth()));
        } catch (IOException e) {
            return toErrorResponse(new IllegalArgumentException("Could not read uploaded file: " + e.getMessage()));
        } catch (Exception ex) {
            return toErrorResponse(ex);
        }
    }

    @PostMapping("/import")
    public ResponseEntity<?> importProject(@RequestBody ImportRequest request) {
        try {
            if (request.openApiUrl() == null || request.openApiUrl().isBlank()) {
                throw new IllegalArgumentException("openApiUrl is required");
            }
            ApiProject project = projectService.importFromUrl(
                    request.name(), request.openApiUrl(), request.auth(), request.enabledOperationIds());
            return ResponseEntity.ok(project);
        } catch (Exception ex) {
            return toErrorResponse(ex);
        }
    }

    @PostMapping(value = "/import-file", consumes = "multipart/form-data")
    public ResponseEntity<?> importFromFile(@RequestPart("name") String name,
                                             @RequestPart("file") MultipartFile file,
                                             @RequestParam(value = "enabledOperationIds", required = false) Set<String> enabledOperationIds,
                                             @RequestPart(value = "authType", required = false) String authType,
                                             @RequestPart(value = "apiKeyName", required = false) String apiKeyName,
                                             @RequestPart(value = "apiKeyLocation", required = false) String apiKeyLocation,
                                             @RequestPart(value = "apiKeyValue", required = false) String apiKeyValue,
                                             @RequestPart(value = "bearerToken", required = false) String bearerToken,
                                             @RequestPart(value = "basicUsername", required = false) String basicUsername,
                                             @RequestPart(value = "basicPassword", required = false) String basicPassword,
                                             @RequestPart(value = "oauth2TokenUrl", required = false) String oauth2TokenUrl,
                                             @RequestPart(value = "oauth2ClientId", required = false) String oauth2ClientId,
                                             @RequestPart(value = "oauth2ClientSecret", required = false) String oauth2ClientSecret,
                                             @RequestPart(value = "oauth2Scope", required = false) String oauth2Scope,
                                             @RequestPart(value = "extraHeadersJson", required = false) String extraHeadersJson) {
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
            auth.setOauth2TokenUrl(oauth2TokenUrl);
            auth.setOauth2ClientId(oauth2ClientId);
            auth.setOauth2ClientSecret(oauth2ClientSecret);
            auth.setOauth2Scope(oauth2Scope);
            if (extraHeadersJson != null && !extraHeadersJson.isBlank()) {
                auth.setExtraHeaders(JSON.readValue(extraHeadersJson, new TypeReference<Map<String, String>>() {
                }));
            }

            ApiProject project = projectService.importFromContent(name, content, auth, enabledOperationIds);
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

    @GetMapping("/{id}/endpoints")
    public ResponseEntity<List<ProjectService.EndpointInfo>> listEndpoints(@PathVariable String id) {
        return projectService.listEndpoints(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/endpoints")
    public ResponseEntity<?> updateEndpoints(@PathVariable String id, @RequestBody EndpointsRequest request) {
        try {
            Set<String> enabled = request.enabledOperationIds() != null ? request.enabledOperationIds() : Set.of();
            return projectService.updateEnabledEndpoints(id, enabled)
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

    private List<EndpointSummary> toSummaries(List<OpenApiOperation> operations) {
        return operations.stream()
                .map(op -> new EndpointSummary(op.getOperationId(), op.getMethod(), op.getPath(), op.getSummary()))
                .toList();
    }

    private ResponseEntity<?> toErrorResponse(Throwable ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody(ex.getMessage()));
    }

    public record ErrorBody(String error) {
    }
}
