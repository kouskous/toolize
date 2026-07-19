package com.toolize.service;

import com.toolize.domain.ApiAuthConfig;
import com.toolize.domain.ApiProject;
import com.toolize.domain.McpTool;
import com.toolize.domain.OpenApiOperation;
import com.toolize.domain.ToolCustomization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final ToolUsageService toolUsageService;

    public ProjectService(OpenApiImporterService importerService,
                           ToolGeneratorService toolGeneratorService,
                           DynamicToolRegistry toolRegistry,
                           ProjectPersistenceService persistenceService,
                           ToolUsageService toolUsageService) {
        this.importerService = importerService;
        this.toolGeneratorService = toolGeneratorService;
        this.toolRegistry = toolRegistry;
        this.persistenceService = persistenceService;
        this.toolUsageService = toolUsageService;
    }

    /**
     * Result of parsing a spec without importing anything: the discovered
     * endpoints, plus a best-effort authentication suggestion read from the
     * spec's declared security schemes (null if none could be detected).
     */
    public record PreviewResult(List<OpenApiOperation> operations, ApiAuthConfig suggestedAuth) {
    }

    /**
     * Parses a spec without importing anything, so the caller can present the
     * list of discovered endpoints and let the user pick which ones to expose.
     */
    public PreviewResult previewFromUrl(String openApiUrl) {
        OpenApiImporterService.ParsedApi parsed = importerService.parseFromUrl(openApiUrl);
        return new PreviewResult(parsed.operations, parsed.suggestedAuth);
    }

    public PreviewResult previewFromContent(String content) {
        OpenApiImporterService.ParsedApi parsed = importerService.parseFromContent(content);
        return new PreviewResult(parsed.operations, parsed.suggestedAuth);
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
            int discovered = importerService.parseFromContent(project.getRawSpec()).operations.size();
            project.setEnabledOperationIds(new LinkedHashSet<>(enabledOperationIds));
            regenerateTools(project);
            persistenceService.save(project);

            log.info("Updated project '{}' to expose {} of {} endpoints", project.getId(), project.getToolsCount(), discovered);
            return project;
        });
    }

    /**
     * Returns the current LLM-facing description customization for a single
     * tool (empty if none has been set), keyed by the operation's operationId.
     */
    public ToolCustomization getToolCustomization(String projectId, String operationId) {
        return persistenceService.find(projectId)
                .map(ApiProject::getToolCustomizations)
                .map(customizations -> customizations.get(operationId))
                .orElseGet(ToolCustomization::new);
    }

    /**
     * Saves (or clears, if the customization is now empty) the LLM-facing
     * description override for a single tool, then regenerates that
     * project's tools so the change is immediately visible on /mcp.
     */
    public Optional<ApiProject> updateToolCustomization(String projectId, String operationId, ToolCustomization customization) {
        return persistenceService.find(projectId).map(project -> {
            Map<String, ToolCustomization> customizations = new LinkedHashMap<>(project.getToolCustomizations());
            if (customization == null || customization.isEmpty()) {
                customizations.remove(operationId);
            } else {
                customizations.put(operationId, customization);
            }
            project.setToolCustomizations(customizations);
            regenerateTools(project);
            persistenceService.save(project);

            log.info("Updated tool customization for '{}' on project '{}'", operationId, project.getId());
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

        regenerateTools(project);
        persistenceService.save(project);

        log.info("Imported project '{}' with {} of {} MCP tools enabled", project.getId(), project.getToolsCount(), parsed.operations.size());
        return project;
    }

    /**
     * Re-parses a project's stored spec, filters it down to the enabled
     * endpoints, generates MCP tools (applying any saved customizations),
     * and replaces whatever that project previously had registered.
     */
    private void regenerateTools(ApiProject project) {
        List<OpenApiOperation> operations = importerService.parseFromContent(project.getRawSpec()).operations;
        List<OpenApiOperation> selected = filterOperations(operations, project.getEnabledOperationIds());
        List<McpTool> tools = toolGeneratorService.generateTools(
                project.getId(), project.getBaseUrl(), selected, project.getToolCustomizations());

        toolRegistry.removeAllForProject(project.getId());
        toolRegistry.registerAll(tools);
        project.setToolsCount(tools.size());
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
        toolUsageService.clearProject(id);
    }

    /**
     * Rebuilds the dynamic tool registry from persisted projects on startup.
     * No restart is needed for *new* imports, but a container restart
     * (e.g. docker restart) must restore previously imported APIs.
     */
    public void rebuildRegistryFromDisk() {
        persistenceService.migrateLegacyJsonIfPresent();
        List<ApiProject> projects = persistenceService.loadAll();
        for (ApiProject project : projects) {
            try {
                regenerateTools(project);
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
