package com.toolize.service;

import com.toolize.domain.ApiAuthConfig;
import com.toolize.domain.ApiProject;
import com.toolize.domain.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
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

    public ApiProject importFromUrl(String name, String openApiUrl, ApiAuthConfig auth) {
        OpenApiImporterService.ParsedApi parsed = importerService.parseFromUrl(openApiUrl);
        return finishImport(name, openApiUrl, parsed, auth);
    }

    public ApiProject importFromContent(String name, String content, ApiAuthConfig auth) {
        OpenApiImporterService.ParsedApi parsed = importerService.parseFromContent(content);
        return finishImport(name, null, parsed, auth);
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

    private ApiProject finishImport(String name, String openApiUrl, OpenApiImporterService.ParsedApi parsed, ApiAuthConfig auth) {
        ApiProject project = new ApiProject();
        project.setId(slugify(name));
        project.setName(name);
        project.setOpenApiUrl(openApiUrl);
        project.setRawSpec(parsed.rawSpec);
        project.setBaseUrl(parsed.baseUrl);
        project.setStatus(ApiProject.Status.ACTIVE);
        project.setAuth(auth);

        List<McpTool> tools = toolGeneratorService.generateTools(project.getId(), parsed.baseUrl, parsed.operations);

        // Replace any previously registered tools for this project (re-import case)
        toolRegistry.removeAllForProject(project.getId());
        toolRegistry.registerAll(tools);

        project.setToolsCount(tools.size());
        persistenceService.save(project);

        log.info("Imported project '{}' with {} MCP tools", project.getId(), tools.size());
        return project;
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
                OpenApiImporterService.ParsedApi parsed = importerService.parseFromContent(project.getRawSpec());
                List<McpTool> tools = toolGeneratorService.generateTools(project.getId(), project.getBaseUrl(), parsed.operations);
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
