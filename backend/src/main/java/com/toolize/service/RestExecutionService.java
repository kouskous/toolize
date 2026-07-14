package com.toolize.service;

import com.toolize.domain.McpTool;
import com.toolize.domain.OpenApiOperation;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
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

    private final WebClient webClient = WebClient.builder().build();

    public ExecutionResult execute(McpTool tool, Map<String, Object> arguments) {
        OpenApiOperation op = tool.getOperation();
        String path = op.getPath();

        // Replace path parameters
        if (op.getParameters() != null) {
            for (OpenApiOperation.OpenApiParameter p : op.getParameters()) {
                if ("path".equals(p.getIn()) && arguments.containsKey(p.getName())) {
                    path = path.replace("{" + p.getName() + "}", String.valueOf(arguments.get(p.getName())));
                }
            }
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(tool.getBaseUrl() + path);

        if (op.getParameters() != null) {
            for (OpenApiOperation.OpenApiParameter p : op.getParameters()) {
                if ("query".equals(p.getIn()) && arguments.containsKey(p.getName())) {
                    uriBuilder.queryParam(p.getName(), String.valueOf(arguments.get(p.getName())));
                }
            }
        }

        String uri = uriBuilder.build().toUriString();
        HttpMethod method = HttpMethod.valueOf(op.getMethod().toUpperCase());

        WebClient.RequestBodySpec requestSpec = webClient.method(method).uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        WebClient.RequestHeadersSpec<?> finalRequest;
        Object body = arguments.get("body");
        if (body != null && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH)) {
            finalRequest = requestSpec.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
        } else {
            finalRequest = requestSpec;
        }

        // Header parameters
        if (op.getParameters() != null) {
            for (OpenApiOperation.OpenApiParameter p : op.getParameters()) {
                if ("header".equals(p.getIn()) && arguments.containsKey(p.getName())) {
                    finalRequest = finalRequest.header(p.getName(), String.valueOf(arguments.get(p.getName())));
                }
            }
        }

        try {
            return finalRequest.exchangeToMono(response ->
                    response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(responseBody -> new ExecutionResult(response.statusCode().value(), responseBody))
            ).block();
        } catch (Exception e) {
            return new ExecutionResult(502, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
