package com.toolize.service;

import com.toolize.domain.ApiAuthConfig;
import com.toolize.domain.ApiProject;
import com.toolize.domain.McpTool;
import com.toolize.domain.OpenApiOperation;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Executes the actual HTTP call to the external REST API represented
 * by an MCP tool, using the arguments provided by the MCP client.
 */
@Service
public class RestExecutionService {

    public static class ExecutionResult {
        public final int status;
        public final String body;

        public ExecutionResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private final RestClient restClient = RestClient.builder().build();
    private final ProjectPersistenceService persistenceService;

    public RestExecutionService(ProjectPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public ExecutionResult execute(McpTool tool, Map<String, Object> arguments) {
        OpenApiOperation op = tool.getOperation();
        String path = op.getPath();
        ApiAuthConfig auth = persistenceService.find(tool.getProjectId())
                .map(ApiProject::getAuth)
                .orElseGet(ApiAuthConfig::new);

        // Replace path parameters
        if (op.getParameters() != null) {
            for (OpenApiOperation.OpenApiParameter p : op.getParameters()) {
                if ("path".equals(p.getIn()) && arguments.containsKey(p.getName())) {
                    path = path.replace("{" + p.getName() + "}", String.valueOf(arguments.get(p.getName())));
                }
            }
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(tool.getBaseUrl() + path);

        if (op.getParameters() != null) {
            for (OpenApiOperation.OpenApiParameter p : op.getParameters()) {
                if ("query".equals(p.getIn()) && arguments.containsKey(p.getName())) {
                    uriBuilder.queryParam(p.getName(), String.valueOf(arguments.get(p.getName())));
                }
            }
        }

        if (auth.getType() == ApiAuthConfig.AuthType.API_KEY
                && auth.getApiKeyLocation() == ApiAuthConfig.ApiKeyLocation.QUERY
                && auth.getApiKeyName() != null && !auth.getApiKeyName().isBlank()) {
            uriBuilder.queryParam(auth.getApiKeyName(), auth.getApiKeyValue());
        }

        String uri = uriBuilder.build().toUriString();
        HttpMethod method = HttpMethod.valueOf(op.getMethod().toUpperCase());

        RestClient.RequestBodySpec requestSpec = restClient.method(method).uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        // Header parameters
        if (op.getParameters() != null) {
            for (OpenApiOperation.OpenApiParameter p : op.getParameters()) {
                if ("header".equals(p.getIn()) && arguments.containsKey(p.getName())) {
                    requestSpec = requestSpec.header(p.getName(), String.valueOf(arguments.get(p.getName())));
                }
            }
        }

        requestSpec = applyAuth(requestSpec, auth);

        RestClient.RequestHeadersSpec<?> finalRequest;
        Object body = arguments.get("body");
        if (body != null && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH)) {
            finalRequest = requestSpec.contentType(MediaType.APPLICATION_JSON).body(body);
        } else {
            finalRequest = requestSpec;
        }

        try {
            return finalRequest.exchange((request, response) -> {
                String responseBody = response.bodyTo(String.class);
                return new ExecutionResult(response.getStatusCode().value(),
                        responseBody != null ? responseBody : "");
            });
        } catch (Exception e) {
            return new ExecutionResult(502, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private RestClient.RequestBodySpec applyAuth(RestClient.RequestBodySpec requestSpec, ApiAuthConfig auth) {
        switch (auth.getType()) {
            case API_KEY -> {
                if (auth.getApiKeyLocation() == ApiAuthConfig.ApiKeyLocation.HEADER
                        && auth.getApiKeyName() != null && !auth.getApiKeyName().isBlank()) {
                    return requestSpec.header(auth.getApiKeyName(), auth.getApiKeyValue());
                }
            }
            case BEARER_TOKEN -> {
                if (auth.getBearerToken() != null && !auth.getBearerToken().isBlank()) {
                    return requestSpec.header("Authorization", "Bearer " + auth.getBearerToken());
                }
            }
            case BASIC_AUTH -> {
                if (auth.getBasicUsername() != null && !auth.getBasicUsername().isBlank()) {
                    String password = auth.getBasicPassword() != null ? auth.getBasicPassword() : "";
                    return requestSpec.headers(h -> h.setBasicAuth(auth.getBasicUsername(), password));
                }
            }
            case NONE -> {
                // no-op
            }
        }
        return requestSpec;
    }
}
