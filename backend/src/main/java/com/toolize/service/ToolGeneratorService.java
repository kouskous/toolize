package com.toolize.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.toolize.domain.McpTool;
import com.toolize.domain.OpenApiOperation;
import com.toolize.domain.ToolCustomization;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Generates {@link McpTool} definitions (name, description, JSON input schema)
 * from parsed OpenAPI operations, layering any user-provided {@link ToolCustomization}
 * on top of whatever the spec itself says so LLMs get the clearest possible
 * picture of what a tool does and how to call it.
 */
@Service
public class ToolGeneratorService {

    private final ObjectMapper mapper = new JsonMapper();

    public List<McpTool> generateTools(String projectId, String baseUrl, List<OpenApiOperation> operations,
                                        Map<String, ToolCustomization> customizations) {
        return operations.stream()
                .map(op -> toTool(projectId, baseUrl, op, customizations))
                .toList();
    }

    private McpTool toTool(String projectId, String baseUrl, OpenApiOperation op,
                            Map<String, ToolCustomization> customizations) {
        String name = sanitizeToolName(op.getOperationId());
        ToolCustomization customization = customizations != null ? customizations.get(op.getOperationId()) : null;

        String description = firstNonBlank(
                customization != null ? customization.getDescription() : null,
                op.getDescription(),
                op.getSummary());

        JsonNode schema = buildInputSchema(op, customization);
        return new McpTool(name, description, projectId, baseUrl, op, schema);
    }

    private String sanitizeToolName(String operationId) {
        // MCP tool names should be simple identifiers
        return operationId.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private JsonNode buildInputSchema(OpenApiOperation op, ToolCustomization customization) {
        Map<String, String> paramOverrides = customization != null ? customization.getParameterDescriptions() : Map.of();

        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ArrayNode required = mapper.createArrayNode();

        if (op.getParameters() != null) {
            for (OpenApiOperation.OpenApiParameter p : op.getParameters()) {
                ObjectNode paramSchema = mapper.createObjectNode();
                paramSchema.put("type", mapJsonType(p.getType()));
                String description = firstNonBlank(
                        paramOverrides.get(p.getName()),
                        p.getDescription(),
                        p.getIn() + " parameter");
                paramSchema.put("description", description);
                properties.set(p.getName(), paramSchema);
                if (p.isRequired()) {
                    required.add(p.getName());
                }
            }
        }

        if (op.getRequestBodySchemaJson() != null) {
            ObjectNode bodySchema;
            try {
                JsonNode parsed = mapper.readTree(op.getRequestBodySchemaJson());
                bodySchema = parsed instanceof ObjectNode objectNode ? objectNode : mapper.createObjectNode();
            } catch (Exception ignored) {
                bodySchema = mapper.createObjectNode();
                bodySchema.put("type", "object");
            }
            String bodyOverride = paramOverrides.get("body");
            if (bodyOverride != null && !bodyOverride.isBlank()) {
                bodySchema.put("description", bodyOverride);
            } else if (!bodySchema.has("description")) {
                bodySchema.put("description", "Request body");
            }
            properties.set("body", bodySchema);
        }

        schema.set("properties", properties);
        if (!required.isEmpty()) {
            schema.set("required", required);
        }
        return schema;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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
