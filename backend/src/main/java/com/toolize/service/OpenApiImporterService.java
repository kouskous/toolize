package com.toolize.service;

import com.toolize.domain.OpenApiOperation;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses an OpenAPI/Swagger specification (from a URL or raw YAML/JSON content)
 * and extracts the list of operations plus the API's base URL.
 */
@Service
public class OpenApiImporterService {

    public static class ParsedApi {
        public final String baseUrl;
        public final List<OpenApiOperation> operations;
        public final String rawSpec;

        public ParsedApi(String baseUrl, List<OpenApiOperation> operations, String rawSpec) {
            this.baseUrl = baseUrl;
            this.operations = operations;
            this.rawSpec = rawSpec;
        }
    }

    /**
     * Parse a specification located at a URL (OpenAPI URL or Swagger URL).
     */
    public ParsedApi parseFromUrl(String url) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readLocation(url, null, options);
        return toParsedApi(result, url);
    }

    /**
     * Parse a specification given as raw YAML or JSON content (uploaded file).
     */
    public ParsedApi parseFromContent(String content) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readContents(content, null, options);
        return toParsedApi(result, null);
    }

    private ParsedApi toParsedApi(SwaggerParseResult result, String fallbackUrl) {
        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null) {
            String reason = (result.getMessages() == null || result.getMessages().isEmpty())
                    ? "Unable to parse OpenAPI specification"
                    : String.join("; ", result.getMessages());
            throw new IllegalArgumentException(reason);
        }

        String baseUrl = resolveBaseUrl(openAPI, fallbackUrl);
        List<OpenApiOperation> operations = extractOperations(openAPI);
        String rawSpec = com.toolize.util.YamlJsonUtil.toJson(openAPI);

        return new ParsedApi(baseUrl, operations, rawSpec);
    }

    private String resolveBaseUrl(OpenAPI openAPI, String fallbackUrl) {
        List<Server> servers = openAPI.getServers();
        if (servers != null && !servers.isEmpty()) {
            String url = servers.get(0).getUrl();
            if (url != null && !url.isBlank()) {
                return url;
            }
        }
        if (fallbackUrl != null) {
            // fallback: strip the spec file path to guess a base url (best-effort)
            int idx = fallbackUrl.indexOf("/", fallbackUrl.indexOf("://") + 3);
            if (idx > 0) {
                return fallbackUrl.substring(0, idx);
            }
            return fallbackUrl;
        }
        return "";
    }

    private List<OpenApiOperation> extractOperations(OpenAPI openAPI) {
        List<OpenApiOperation> operations = new ArrayList<>();
        Paths paths = openAPI.getPaths();
        if (paths == null) {
            return operations;
        }

        for (Map.Entry<String, PathItem> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();

            addOperation(operations, path, "GET", pathItem.getGet());
            addOperation(operations, path, "POST", pathItem.getPost());
            addOperation(operations, path, "PUT", pathItem.getPut());
            addOperation(operations, path, "PATCH", pathItem.getPatch());
            addOperation(operations, path, "DELETE", pathItem.getDelete());
        }
        return operations;
    }

    private void addOperation(List<OpenApiOperation> operations, String path, String method, Operation op) {
        if (op == null) {
            return;
        }

        String operationId = op.getOperationId();
        if (operationId == null || operationId.isBlank()) {
            operationId = generateOperationId(method, path);
        }

        List<OpenApiOperation.OpenApiParameter> parameters = new ArrayList<>();
        if (op.getParameters() != null) {
            for (Parameter p : op.getParameters()) {
                String type = "string";
                Schema<?> schema = p.getSchema();
                if (schema != null && schema.getType() != null) {
                    type = schema.getType();
                }
                parameters.add(new OpenApiOperation.OpenApiParameter(
                        p.getName(), p.getIn(), Boolean.TRUE.equals(p.getRequired()), type));
            }
        }

        String requestBodySchemaJson = null;
        RequestBody body = op.getRequestBody();
        if (body != null) {
            Content content = body.getContent();
            if (content != null) {
                MediaType mt = content.get("application/json");
                if (mt != null && mt.getSchema() != null) {
                    requestBodySchemaJson = com.toolize.util.YamlJsonUtil.toJson(mt.getSchema());
                }
            }
        }

        String summary = op.getSummary() != null ? op.getSummary() : (method + " " + path);

        operations.add(new OpenApiOperation(operationId, method, path, summary, parameters, requestBodySchemaJson));
    }

    private String generateOperationId(String method, String path) {
        String cleaned = path.replaceAll("[{}]", "").replaceAll("[/\\-]", "_");
        return method.toLowerCase() + cleaned;
    }
}
