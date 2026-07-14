package com.toolize.controller;

import com.toolize.domain.McpTool;
import com.toolize.service.DynamicToolRegistry;
import com.toolize.service.RestExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/tools")
public class ToolController {

    private final DynamicToolRegistry registry;
    private final RestExecutionService executionService;

    public ToolController(DynamicToolRegistry registry, RestExecutionService executionService) {
        this.registry = registry;
        this.executionService = executionService;
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
}
