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
    private final OAuth2TokenService oAuth2TokenService;
    private final ToolUsageService usageService;

    public RestExecutionService(ProjectPersistenceService persistenceService, OAuth2TokenService oAuth2TokenService,
                                 ToolUsageService usageService) {
        this.persistenceService = persistenceService;
        this.oAuth2TokenService = oAuth2TokenService;
        this.usageService = usageService;
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
        requestSpec = applyExtraHeaders(requestSpec, auth);

        RestClient.RequestHeadersSpec<?> finalRequest;
        Object body = arguments.get("body");
        if (body != null && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH)) {
            finalRequest = requestSpec.contentType(MediaType.APPLICATION_JSON).body(body);
        } else {
            finalRequest = requestSpec;
        }

        long startedAt = System.nanoTime();
        ExecutionResult result;
        try {
            result = finalRequest.exchange((request, response) -> {
                String responseBody = response.bodyTo(String.class);
                return new ExecutionResult(response.getStatusCode().value(),
                        responseBody != null ? responseBody : "");
            });
        } catch (Exception e) {
            result = new ExecutionResult(502, "{\"error\":\"" + e.getMessage() + "\"}");
        }

        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
        usageService.recordCall(tool.getProjectId(), op.getOperationId(), result.status, latencyMs);
        return result;
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
            case OAUTH2_CLIENT_CREDENTIALS -> {
                String token = oAuth2TokenService.getAccessToken(auth);
                return requestSpec.header("Authorization", "Bearer " + token);
            }
            case NONE -> {
                // no-op
            }
        }
        return requestSpec;
    }

    private RestClient.RequestBodySpec applyExtraHeaders(RestClient.RequestBodySpec requestSpec, ApiAuthConfig auth) {
        Map<String, String> extraHeaders = auth.getExtraHeaders();
        if (extraHeaders == null || extraHeaders.isEmpty()) {
            return requestSpec;
        }
        RestClient.RequestBodySpec result = requestSpec;
        for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
            if (header.getKey() != null && !header.getKey().isBlank()) {
                result = result.header(header.getKey(), header.getValue());
            }
        }
        return result;
    }
}
