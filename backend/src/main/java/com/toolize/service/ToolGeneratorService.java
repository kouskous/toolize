package com.toolize.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.toolize.domain.McpTool;
import com.toolize.domain.OpenApiOperation;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Generates {@link McpTool} definitions (name, description, JSON input schema)
 * from parsed OpenAPI operations.
 */
@Service
public class ToolGeneratorService {

    private final ObjectMapper mapper = new JsonMapper();

    public List<McpTool> generateTools(String projectId, String baseUrl, List<OpenApiOperation> operations) {
        return operations.stream()
                .map(op -> toTool(projectId, baseUrl, op))
                .toList();
    }

    private McpTool toTool(String projectId, String baseUrl, OpenApiOperation op) {
        String name = sanitizeToolName(op.getOperationId());
        JsonNode schema = buildInputSchema(op);
        return new McpTool(name, op.getSummary(), projectId, baseUrl, op, schema);
    }

    private String sanitizeToolName(String operationId) {
        // MCP tool names should be simple identifiers
        return operationId.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private JsonNode buildInputSchema(OpenApiOperation op) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ArrayNode required = mapper.createArrayNode();

        if (op.getParameters() != null) {
            for (OpenApiOperation.OpenApiParameter p : op.getParameters()) {
                ObjectNode paramSchema = mapper.createObjectNode();
                paramSchema.put("type", mapJsonType(p.getType()));
                paramSchema.put("description", p.getIn() + " parameter");
                properties.set(p.getName(), paramSchema);
                if (p.isRequired()) {
                    required.add(p.getName());
                }
            }
        }

        if (op.getRequestBodySchemaJson() != null) {
            try {
                JsonNode bodySchema = mapper.readTree(op.getRequestBodySchemaJson());
                properties.set("body", bodySchema);
            } catch (Exception ignored) {
                ObjectNode fallback = mapper.createObjectNode();
                fallback.put("type", "object");
                fallback.put("description", "Request body");
                properties.set("body", fallback);
            }
        }

        schema.set("properties", properties);
        if (!required.isEmpty()) {
            schema.set("required", required);
        }
        return schema;
    }

    private String mapJsonType(String openApiType) {
        if (openApiType == null) {
            return "string";
        }
        return switch (openApiType) {
            case "integer", "number" -> openApiType;
            case "boolean" -> "boolean";
            case "array" -> "array";
            default -> "string";
        };
    }
}
