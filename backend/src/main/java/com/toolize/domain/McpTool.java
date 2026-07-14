package com.toolize.domain;

import tools.jackson.databind.JsonNode;

/**
 * A dynamically generated MCP tool, backed by a REST operation
 * from an imported OpenAPI specification.
 */
public class McpTool {

    private String name;          // e.g. getCustomer
    private String description;
    private String projectId;     // owning project
    private String baseUrl;       // resolved server base url of the API
    private OpenApiOperation operation;
    private JsonNode inputSchema;  // JSON schema describing tool arguments

    public McpTool() {
    }

    public McpTool(String name, String description, String projectId, String baseUrl,
                   OpenApiOperation operation, JsonNode inputSchema) {
        this.name = name;
        this.description = description;
        this.projectId = projectId;
        this.baseUrl = baseUrl;
        this.operation = operation;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public OpenApiOperation getOperation() {
        return operation;
    }

    public void setOperation(OpenApiOperation operation) {
        this.operation = operation;
    }

    public JsonNode getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(JsonNode inputSchema) {
        this.inputSchema = inputSchema;
    }
}
