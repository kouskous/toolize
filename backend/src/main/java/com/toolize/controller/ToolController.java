package com.toolize.controller;

import com.toolize.domain.McpTool;
import com.toolize.domain.OpenApiOperation;
import com.toolize.domain.ToolCustomization;
import com.toolize.service.DynamicToolRegistry;
import com.toolize.service.ProjectService;
import com.toolize.service.RestExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects/{projectId}/tools")
public class ToolController {

    private static final JsonMapper JSON = new JsonMapper();

    private final DynamicToolRegistry registry;
    private final RestExecutionService executionService;
    private final ProjectService projectService;

    public ToolController(DynamicToolRegistry registry, RestExecutionService executionService,
                           ProjectService projectService) {
        this.registry = registry;
        this.executionService = executionService;
        this.projectService = projectService;
    }

    public record ToolSummary(String name, String description, String method, String path) {
    }

    @GetMapping
    public List<ToolSummary> listTools(@PathVariable String projectId) {
        return registry.listForProject(projectId).stream()
                .map(t -> new ToolSummary(t.getName(), t.getDescription(),
                        t.getOperation().getMethod(), t.getOperation().getPath()))
                .toList();
    }

    @GetMapping("/{toolName}")
    public ResponseEntity<McpTool> getTool(@PathVariable String projectId, @PathVariable String toolName) {
        return registry.find(toolName)
                .filter(t -> t.getProjectId().equals(projectId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record ParameterDefault(String name, String in, boolean required, String type, String defaultDescription) {
    }

    public record CustomizationView(String operationId, String toolName, String defaultDescription,
                                     List<ParameterDefault> parameters, boolean hasBody, String bodyDefaultDescription,
                                     ToolCustomization customization) {
    }

    @GetMapping("/{toolName}/customize")
    public ResponseEntity<CustomizationView> getCustomization(@PathVariable String projectId, @PathVariable String toolName) {
        Optional<McpTool> tool = registry.find(toolName).filter(t -> t.getProjectId().equals(projectId));
        if (tool.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        OpenApiOperation op = tool.get().getOperation();
        List<ParameterDefault> parameters = op.getParameters() == null ? List.of() : op.getParameters().stream()
                .map(p -> new ParameterDefault(p.getName(), p.getIn(), p.isRequired(), p.getType(), p.getDescription()))
                .toList();

        String defaultDescription = firstNonBlank(op.getDescription(), op.getSummary());
        String bodyDefaultDescription = op.getRequestBodySchemaJson() != null
                ? extractBodyDescription(op.getRequestBodySchemaJson())
                : null;
        ToolCustomization customization = projectService.getToolCustomization(projectId, op.getOperationId());

        return ResponseEntity.ok(new CustomizationView(op.getOperationId(), tool.get().getName(), defaultDescription,
                parameters, op.getRequestBodySchemaJson() != null, bodyDefaultDescription, customization));
    }

    @PutMapping("/{toolName}/customize")
    public ResponseEntity<?> updateCustomization(@PathVariable String projectId, @PathVariable String toolName,
                                                  @RequestBody ToolCustomization customization) {
        Optional<McpTool> tool = registry.find(toolName).filter(t -> t.getProjectId().equals(projectId));
        if (tool.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String operationId = tool.get().getOperation().getOperationId();
        return projectService.updateToolCustomization(projectId, operationId, customization)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record ExecuteRequest(Map<String, Object> arguments) {
    }

    public record ExecuteResponse(int status, Object body) {
    }

    @PostMapping("/{toolName}/execute")
    public ResponseEntity<?> executeTool(@PathVariable String projectId,
                                          @PathVariable String toolName,
                                          @RequestBody(required = false) ExecuteRequest request) {
        try {
            McpTool tool = registry.find(toolName)
                    .filter(t -> t.getProjectId().equals(projectId))
                    .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + toolName));

            Map<String, Object> args = (request != null && request.arguments() != null)
                    ? request.arguments() : Map.of();

            RestExecutionService.ExecutionResult result = executionService.execute(tool, args);
            return ResponseEntity.ok(new ExecuteResponse(result.status, result.body));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    private String extractBodyDescription(String requestBodySchemaJson) {
        try {
            JsonNode node = JSON.readTree(requestBodySchemaJson);
            JsonNode description = node.get("description");
            return description != null && description.isString() ? description.asString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
