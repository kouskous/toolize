package com.toolize.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toolize.domain.McpTool;
import com.toolize.service.DynamicToolRegistry;
import com.toolize.service.RestExecutionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Minimal MCP server, exposed over HTTP using JSON-RPC 2.0, matching the
 * "Streamable HTTP" transport request/response shape (without SSE streaming,
 * which is sufficient for tool discovery and invocation).
 *
 * Supported methods:
 *  - initialize
 *  - tools/list
 *  - tools/call
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private final DynamicToolRegistry registry;
    private final RestExecutionService executionService;
    private final ObjectMapper mapper = new ObjectMapper();

    public McpController(DynamicToolRegistry registry, RestExecutionService executionService) {
        this.registry = registry;
        this.executionService = executionService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ObjectNode> handle(@RequestBody JsonNode request) {
        String method = request.path("method").asText("");
        JsonNode id = request.get("id");
        JsonNode params = request.path("params");

        return switch (method) {
            case "initialize" -> Mono.just(success(id, buildInitializeResult()));
            case "tools/list" -> Mono.just(success(id, buildToolsListResult()));
            case "tools/call" -> Mono.fromCallable(() -> success(id, buildToolsCallResult(params)))
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(ex -> Mono.just(error(id, -32000, ex.getMessage())));
            default -> Mono.just(error(id, -32601, "Method not found: " + method));
        };
    }

    private ObjectNode buildInitializeResult() {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        ObjectNode capabilities = mapper.createObjectNode();
        ObjectNode toolsCapability = mapper.createObjectNode();
        toolsCapability.put("listChanged", true);
        capabilities.set("tools", toolsCapability);
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = mapper.createObjectNode();
        serverInfo.put("name", "toolize");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);

        return result;
    }

    private ObjectNode buildToolsListResult() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode toolsArray = mapper.createArrayNode();

        for (McpTool tool : registry.listAll()) {
            ObjectNode toolNode = mapper.createObjectNode();
            toolNode.put("name", tool.getName());
            toolNode.put("description", tool.getDescription() != null ? tool.getDescription() : "");
            toolNode.set("inputSchema", tool.getInputSchema());
            toolsArray.add(toolNode);
        }

        result.set("tools", toolsArray);
        return result;
    }

    private ObjectNode buildToolsCallResult(JsonNode params) {
        String name = params.path("name").asText(null);
        if (name == null) {
            throw new IllegalArgumentException("Missing 'name' in tools/call params");
        }

        McpTool tool = registry.find(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + name));

        Map<String, Object> arguments = new HashMap<>();
        JsonNode argsNode = params.path("arguments");
        if (argsNode.isObject()) {
            Iterator<String> fieldNames = argsNode.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                arguments.put(field, mapper.convertValue(argsNode.get(field), Object.class));
            }
        }

        RestExecutionService.ExecutionResult execResult = executionService.execute(tool, arguments);

        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = mapper.createArrayNode();
        ObjectNode textContent = mapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", "HTTP " + execResult.status + "\n" + execResult.body);
        content.add(textContent);
        result.set("content", content);
        result.put("isError", execResult.status >= 400);

        return result;
    }

    private ObjectNode success(JsonNode id, ObjectNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode err = mapper.createObjectNode();
        err.put("code", code);
        err.put("message", message != null ? message : "Unknown error");
        response.set("error", err);
        return response;
    }
}
