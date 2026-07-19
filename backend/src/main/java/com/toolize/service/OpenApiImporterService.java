package com.toolize.service;

import com.toolize.domain.ApiAuthConfig;
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
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
        public final ApiAuthConfig suggestedAuth;

        public ParsedApi(String baseUrl, List<OpenApiOperation> operations, String rawSpec, ApiAuthConfig suggestedAuth) {
            this.baseUrl = baseUrl;
            this.operations = operations;
            this.rawSpec = rawSpec;
            this.suggestedAuth = suggestedAuth;
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
        ApiAuthConfig suggestedAuth = detectAuth(openAPI);

        return new ParsedApi(baseUrl, operations, rawSpec, suggestedAuth);
    }

    /**
     * Reads the spec's declared {@code securitySchemes} (and which of them is
     * actually required, via the global {@code security} requirement) and
     * translates the first one found into a ready-to-use {@link ApiAuthConfig}
     * suggestion, so the user doesn't have to guess how the API authenticates.
     * Returns {@code null} when the spec declares no security scheme at all.
     */
    private ApiAuthConfig detectAuth(OpenAPI openAPI) {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSecuritySchemes() == null) {
            return null;
        }
        Map<String, SecurityScheme> schemes = openAPI.getComponents().getSecuritySchemes();
        if (schemes.isEmpty()) {
            return null;
        }

        // Prefer a scheme actually referenced by the global security requirement
        // (order in the spec), falling back to the first declared scheme.
        String preferredName = null;
        if (openAPI.getSecurity() != null) {
            for (var requirement : openAPI.getSecurity()) {
                for (String name : requirement.keySet()) {
                    if (schemes.containsKey(name)) {
                        preferredName = name;
                        break;
                    }
                }
                if (preferredName != null) {
                    break;
                }
            }
        }
        SecurityScheme scheme = preferredName != null ? schemes.get(preferredName) : schemes.values().iterator().next();

        return toAuthConfig(scheme);
    }

    private ApiAuthConfig toAuthConfig(SecurityScheme scheme) {
        ApiAuthConfig auth = new ApiAuthConfig();
        if (scheme.getType() == null) {
            return null;
        }

        switch (scheme.getType()) {
            case HTTP -> {
                String httpScheme = scheme.getScheme() != null ? scheme.getScheme().toLowerCase() : "";
                if ("bearer".equals(httpScheme)) {
                    auth.setType(ApiAuthConfig.AuthType.BEARER_TOKEN);
                } else if ("basic".equals(httpScheme)) {
                    auth.setType(ApiAuthConfig.AuthType.BASIC_AUTH);
                } else {
                    return null;
                }
            }
            case APIKEY -> {
                auth.setType(ApiAuthConfig.AuthType.API_KEY);
                auth.setApiKeyName(scheme.getName());
                auth.setApiKeyLocation(scheme.getIn() != null && "query".equalsIgnoreCase(scheme.getIn().toString())
                        ? ApiAuthConfig.ApiKeyLocation.QUERY
                        : ApiAuthConfig.ApiKeyLocation.HEADER);
            }
            case OAUTH2 -> {
                OAuthFlow clientCredentials = scheme.getFlows() != null ? scheme.getFlows().getClientCredentials() : null;
                if (clientCredentials == null) {
                    // Authorization-code / implicit flows require a browser redirect,
                    // which doesn't fit Toolize's server-to-server tool calls.
                    return null;
                }
                auth.setType(ApiAuthConfig.AuthType.OAUTH2_CLIENT_CREDENTIALS);
                auth.setOauth2TokenUrl(clientCredentials.getTokenUrl());
                if (clientCredentials.getScopes() != null && !clientCredentials.getScopes().isEmpty()) {
                    auth.setOauth2Scope(String.join(" ", clientCredentials.getScopes().keySet()));
                }
            }
            case OPENIDCONNECT -> {
                // No single token endpoint to call without a full OIDC discovery
                // + auth-code dance; suggest bearer so the user can paste a token.
                auth.setType(ApiAuthConfig.AuthType.BEARER_TOKEN);
            }
            default -> {
                return null;
            }
        }
        return auth;
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
