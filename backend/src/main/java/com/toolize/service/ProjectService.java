package com.toolize.service;

import com.toolize.domain.ApiAuthConfig;
import com.toolize.domain.ApiProject;
import com.toolize.domain.McpTool;
import com.toolize.domain.OpenApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the import workflow:
 * OpenAPI spec -> parsed operations -> generated MCP tools -> dynamic registry.
 * This is the core of Toolize: no restart is ever required, tools become
 * available to MCP clients as soon as import() returns.
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final OpenApiImporterService importerService;
    private final ToolGeneratorService toolGeneratorService;
    private final DynamicToolRegistry toolRegistry;
    private final ProjectPersistenceService persistenceService;

    public ProjectService(OpenApiImporterService importerService,
                           ToolGeneratorService toolGeneratorService,
                           DynamicToolRegistry toolRegistry,
                           ProjectPersistenceService persistenceService) {
        this.importerService = importerService;
        this.toolGeneratorService = toolGeneratorService;
        this.toolRegistry = toolRegistry;
        this.persistenceService = persistenceService;
    }

    /**
     * Parses a spec without importing anything, so the caller can present the
     * list of discovered endpoints and let the user pick which ones to expose.
     */
    public List<OpenApiOperation> previewFromUrl(String openApiUrl) {
        return importerService.parseFromUrl(openApiUrl).operations;
    }

    public List<OpenApiOperation> previewFromContent(String content) {
        return importerService.parseFromContent(content).operations;
    }

    public ApiProject importFromUrl(String name, String openApiUrl, ApiAuthConfig auth, Set<String> enabledOperationIds) {
        OpenApiImporterService.ParsedApi parsed = importerService.parseFromUrl(openApiUrl);
        return finishImport(name, openApiUrl, parsed, auth, enabledOperationIds);
    }

    public ApiProject importFromContent(String name, String content, ApiAuthConfig auth, Set<String> enabledOperationIds) {
        OpenApiImporterService.ParsedApi parsed = importerService.parseFromContent(content);
        return finishImport(name, null, parsed, auth, enabledOperationIds);
    }

    /**
     * Updates the authentication config used to call an already-imported API.
     */
    public Optional<ApiProject> updateAuth(String id, ApiAuthConfig auth) {
        return persistenceService.find(id).map(project -> {
            project.setAuth(auth);
            persistenceService.save(project);
            return project;
        });
    }

    /**
     * Lists every endpoint discovered in the project's spec, flagged with
     * whether it is currently enabled (exposed as an MCP tool) or not.
     */
    public record EndpointInfo(String operationId, String method, String path, String summary, boolean enabled) {
    }

    public Optional<List<EndpointInfo>> listEndpoints(String id) {
        return persistenceService.find(id).map(project -> {
            List<OpenApiOperation> operations = importerService.parseFromContent(project.getRawSpec()).operations;
            Set<String> enabled = project.getEnabledOperationIds();
            return operations.stream()
                    .map(op -> new EndpointInfo(op.getOperationId(), op.getMethod(), op.getPath(), op.getSummary(),
                            enabled == null || enabled.contains(op.getOperationId())))
                    .toList();
        });
    }

    /**
     * Re-generates a project's MCP tools from its stored spec, keeping only the
     * given operation ids enabled, without needing to re-import anything.
     */
    public Optional<ApiProject> updateEnabledEndpoints(String id, Set<String> enabledOperationIds) {
        return persistenceService.find(id).map(project -> {
            List<OpenApiOperation> operations = importerService.parseFromContent(project.getRawSpec()).operations;
            List<OpenApiOperation> selected = filterOperations(operations, enabledOperationIds);

            List<McpTool> tools = toolGeneratorService.generateTools(project.getId(), project.getBaseUrl(), selected);
            toolRegistry.removeAllForProject(project.getId());
            toolRegistry.registerAll(tools);

            project.setEnabledOperationIds(new LinkedHashSet<>(enabledOperationIds));
            project.setToolsCount(tools.size());
            persistenceService.save(project);

            log.info("Updated project '{}' to expose {} of {} endpoints", project.getId(), tools.size(), operations.size());
            return project;
        });
    }

    private ApiProject finishImport(String name, String openApiUrl, OpenApiImporterService.ParsedApi parsed,
                                     ApiAuthConfig auth, Set<String> enabledOperationIds) {
        ApiProject project = new ApiProject();
        project.setId(slugify(name));
        project.setName(name);
        project.setOpenApiUrl(openApiUrl);
        project.setRawSpec(parsed.rawSpec);
        project.setBaseUrl(parsed.baseUrl);
        project.setStatus(ApiProject.Status.ACTIVE);
        project.setAuth(auth);

        Set<String> enabled = enabledOperationIds != null
                ? enabledOperationIds
                : allOperationIds(parsed.operations);
        project.setEnabledOperationIds(new LinkedHashSet<>(enabled));

        List<OpenApiOperation> selected = filterOperations(parsed.operations, enabled);
        List<McpTool> tools = toolGeneratorService.generateTools(project.getId(), parsed.baseUrl, selected);

        // Replace any previously registered tools for this project (re-import case)
        toolRegistry.removeAllForProject(project.getId());
        toolRegistry.registerAll(tools);

        project.setToolsCount(tools.size());
        persistenceService.save(project);

        log.info("Imported project '{}' with {} of {} MCP tools enabled", project.getId(), tools.size(), parsed.operations.size());
        return project;
    }

    private List<OpenApiOperation> filterOperations(List<OpenApiOperation> operations, Set<String> enabledOperationIds) {
        if (enabledOperationIds == null) {
            return operations;
        }
        return operations.stream()
                .filter(op -> enabledOperationIds.contains(op.getOperationId()))
                .toList();
    }

    private Set<String> allOperationIds(List<OpenApiOperation> operations) {
        Set<String> ids = new LinkedHashSet<>();
        for (OpenApiOperation op : operations) {
            ids.add(op.getOperationId());
        }
        return ids;
    }

    public void deleteProject(String id) {
        toolRegistry.removeAllForProject(id);
        persistenceService.delete(id);
    }

    /**
     * Rebuilds the dynamic tool registry from persisted projects on startup.
     * No restart is needed for *new* imports, but a container restart
     * (e.g. docker restart) must restore previously imported APIs.
     */
    public void rebuildRegistryFromDisk() {
        List<ApiProject> projects = persistenceService.loadAll();
        for (ApiProject project : projects) {
            try {
                List<OpenApiOperation> operations = importerService.parseFromContent(project.getRawSpec()).operations;
                List<OpenApiOperation> selected = filterOperations(operations, project.getEnabledOperationIds());
                List<McpTool> tools = toolGeneratorService.generateTools(project.getId(), project.getBaseUrl(), selected);
                toolRegistry.registerAll(tools);
                project.setToolsCount(tools.size());
                project.setStatus(ApiProject.Status.ACTIVE);
            } catch (Exception e) {
                log.error("Failed to rebuild tools for project {}: {}", project.getId(), e.getMessage());
                project.setStatus(ApiProject.Status.ERROR);
                project.setErrorMessage(e.getMessage());
            }
        }
        log.info("Rebuilt {} tools across {} projects from disk", toolRegistry.count(), projects.size());
    }

    private String slugify(String name) {
        String base = name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (base.isBlank()) {
            base = "project";
        }
        // ensure uniqueness if a project with this slug already exists
        String candidate = base;
        int suffix = 1;
        while (persistenceService.find(candidate).isPresent()) {
            candidate = base + "-" + (++suffix);
        }
        return candidate;
    }

    public String generateId() {
        return UUID.randomUUID().toString();
    }
}
