package com.toolize.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the core product loop: import an OpenAPI spec over the
 * REST API, confirm the generated tools are immediately visible on the real
 * /mcp JSON-RPC endpoint (no restart), and that a saved description
 * customization is reflected there too.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpFlowIntegrationTest {

    private static final String SPEC = """
            {
              "openapi": "3.0.3",
              "info": { "title": "Test Bookstore API", "version": "1.0.0" },
              "servers": [{ "url": "https://api.bookstore.example.com" }],
              "paths": {
                "/books": {
                  "get": {
                    "operationId": "listBooks",
                    "summary": "List all books",
                    "responses": { "200": { "description": "OK" } }
                  }
                },
                "/books/{bookId}": {
                  "get": {
                    "operationId": "getBook",
                    "summary": "Get a book by ID",
                    "parameters": [
                      { "name": "bookId", "in": "path", "required": true, "schema": { "type": "string" } }
                    ],
                    "responses": { "200": { "description": "OK" } }
                  }
                }
              }
            }
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String USER = "admin";
    private static final String PASSWORD = "test-admin-password";

    @LocalServerPort
    private int port;

    private String importedProjectId;

    private RestClient restClient() {
        return RestClient.create("http://localhost:" + port);
    }

    @AfterEach
    void cleanUp() {
        if (importedProjectId != null) {
            restClient().delete().uri("/api/projects/" + importedProjectId)
                    .headers(h -> h.setBasicAuth(USER, PASSWORD))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    private String importTestProject() {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("name", "MCP Flow Test API");
        form.add("file", new ByteArrayResource(SPEC.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "spec.json";
            }
        });

        String body = restClient().post().uri("/api/projects/import-file")
                .headers(h -> {
                    h.setBasicAuth(USER, PASSWORD);
                    h.setContentType(MediaType.MULTIPART_FORM_DATA);
                })
                .body(form)
                .retrieve()
                .body(String.class);

        JsonNode project = JSON.readTree(body);
        return project.path("id").asString();
    }

    private JsonNode callMcp(String jsonRpcBody) {
        String responseBody = restClient().post().uri("/mcp")
                .headers(h -> {
                    h.setBasicAuth(USER, PASSWORD);
                    h.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(jsonRpcBody)
                .retrieve()
                .body(String.class);
        return JSON.readTree(responseBody);
    }

    @Test
    void importedToolsAreImmediatelyVisibleOnTheMcpEndpoint() {
        importedProjectId = importTestProject();

        JsonNode toolsList = callMcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        JsonNode tools = toolsList.path("result").path("tools");

        assertThat(tools.isArray()).isTrue();
        var toolNames = new ArrayList<String>();
        tools.forEach(t -> toolNames.add(t.path("name").asString()));
        assertThat(toolNames).contains("listBooks", "getBook");
    }

    @Test
    void aSavedDescriptionCustomizationShowsUpOnTheMcpEndpointWithoutReimporting() {
        importedProjectId = importTestProject();

        String customizationBody = "{\"description\":\"Use this to look up a single book before recommending it.\"}";
        restClient().method(HttpMethod.PUT).uri("/api/projects/" + importedProjectId + "/tools/getBook/customize")
                .headers(h -> {
                    h.setBasicAuth(USER, PASSWORD);
                    h.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(customizationBody)
                .retrieve()
                .toBodilessEntity();

        JsonNode toolsList = callMcp("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        JsonNode getBookTool = StreamSupport.stream(toolsList.path("result").path("tools").spliterator(), false)
                .filter(t -> "getBook".equals(t.path("name").asString()))
                .findFirst()
                .orElseThrow();

        assertThat(getBookTool.path("description").asString())
                .isEqualTo("Use this to look up a single book before recommending it.");
    }

    @Test
    void unknownToolNameReturnsAJsonRpcErrorNotAServerError() {
        JsonNode response = callMcp("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"doesNotExist\"}}");

        assertThat(response.path("error").path("message").asString()).contains("Unknown tool");
    }
}
