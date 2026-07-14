package com.toolize.domain;

import java.util.List;

/**
 * A single operation extracted from an OpenAPI specification
 * (e.g. GET /customers/{id}).
 */
public class OpenApiOperation {

    private String operationId;
    private String method;
    private String path;
    private String summary;
    private List<OpenApiParameter> parameters;
    private String requestBodySchemaJson; // raw JSON schema for request body, may be null

    public OpenApiOperation() {
    }

    public OpenApiOperation(String operationId, String method, String path, String summary,
                             List<OpenApiParameter> parameters, String requestBodySchemaJson) {
        this.operationId = operationId;
        this.method = method;
        this.path = path;
        this.summary = summary;
        this.parameters = parameters;
        this.requestBodySchemaJson = requestBodySchemaJson;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<OpenApiParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<OpenApiParameter> parameters) {
        this.parameters = parameters;
    }

    public String getRequestBodySchemaJson() {
        return requestBodySchemaJson;
    }

    public void setRequestBodySchemaJson(String requestBodySchemaJson) {
        this.requestBodySchemaJson = requestBodySchemaJson;
    }

    /**
     * A single parameter of an operation (path, query, header).
     */
    public static class OpenApiParameter {
        private String name;
        private String in; // "path", "query", "header"
        private boolean required;
        private String type; // string, integer, boolean, etc.

        public OpenApiParameter() {
        }

        public OpenApiParameter(String name, String in, boolean required, String type) {
            this.name = name;
            this.in = in;
            this.required = required;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIn() {
            return in;
        }

        public void setIn(String in) {
            this.in = in;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
