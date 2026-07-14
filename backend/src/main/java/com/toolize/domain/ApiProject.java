package com.toolize.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Represents an imported API (one OpenAPI specification -> a set of MCP tools).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiProject {

    public enum Status {
        ACTIVE,
        ERROR
    }

    private String id;
    private String name;
    private String openApiUrl;   // may be null if imported from an uploaded file
    private String rawSpec;      // raw spec content, stored so we can rebuild tools on restart
    private String baseUrl;      // resolved server base url
    private int toolsCount;
    private Status status = Status.ACTIVE;
    private String errorMessage;
    private Instant importedAt = Instant.now();

    public ApiProject() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOpenApiUrl() {
        return openApiUrl;
    }

    public void setOpenApiUrl(String openApiUrl) {
        this.openApiUrl = openApiUrl;
    }

    public String getRawSpec() {
        return rawSpec;
    }

    public void setRawSpec(String rawSpec) {
        this.rawSpec = rawSpec;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getToolsCount() {
        return toolsCount;
    }

    public void setToolsCount(int toolsCount) {
        this.toolsCount = toolsCount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }
}
