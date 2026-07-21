package com.toolize.service;

import com.toolize.domain.OpenApiOperation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiImporterServiceTest {

    private final OpenApiImporterService importer = new OpenApiImporterService();

    @Test
    void extractsOperationIdMethodPathAndSummary() {
        String spec = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "Test API", "version": "1.0.0" },
                  "servers": [{ "url": "https://api.example.com" }],
                  "paths": {
                    "/books": {
                      "get": {
                        "operationId": "listBooks",
                        "summary": "List all books",
                        "responses": { "200": { "description": "OK" } }
                      }
                    }
                  }
                }
                """;

        OpenApiImporterService.ParsedApi parsed = importer.parseFromContent(spec);

        assertThat(parsed.baseUrl).isEqualTo("https://api.example.com");
        assertThat(parsed.operations).hasSize(1);
        OpenApiOperation op = parsed.operations.get(0);
        assertThat(op.getOperationId()).isEqualTo("listBooks");
        assertThat(op.getMethod()).isEqualTo("GET");
        assertThat(op.getPath()).isEqualTo("/books");
        assertThat(op.getSummary()).isEqualTo("List all books");
    }

    /**
     * Regression test for a real bug found while testing: two endpoints sharing
     * the same operationId collapsed into a single MCP tool and a single
     * checkbox in the endpoint selector, since Toolize keys everything by
     * operationId. Every collision after the first must be renamed so each
     * endpoint stays independently selectable and addressable.
     */
    @Test
    void deduplicatesOperationIdsThatCollideAcrossDifferentEndpoints() {
        String spec = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "Dup Test API", "version": "1.0.0" },
                  "servers": [{ "url": "https://api.example.com" }],
                  "paths": {
                    "/widgets": {
                      "get": { "operationId": "list", "responses": { "200": { "description": "OK" } } }
                    },
                    "/gadgets": {
                      "get": { "operationId": "list", "responses": { "200": { "description": "OK" } } }
                    },
                    "/sprockets": {
                      "get": { "operationId": "listSprockets", "responses": { "200": { "description": "OK" } } }
                    }
                  }
                }
                """;

        List<OpenApiOperation> operations = importer.parseFromContent(spec).operations;

        List<String> operationIds = operations.stream().map(OpenApiOperation::getOperationId).toList();
        assertThat(operationIds).containsExactlyInAnyOrder("list", "list_2", "listSprockets");
        assertThat(operationIds).doesNotHaveDuplicates();

        // The path each id maps back to must still be recoverable and distinct.
        String widgetsOpId = operations.stream().filter(op -> op.getPath().equals("/widgets")).findFirst().orElseThrow().getOperationId();
        String gadgetsOpId = operations.stream().filter(op -> op.getPath().equals("/gadgets")).findFirst().orElseThrow().getOperationId();
        assertThat(widgetsOpId).isNotEqualTo(gadgetsOpId);
    }

    @Test
    void generatesAnOperationIdWhenTheSpecOmitsOne() {
        String spec = """
                {
                  "openapi": "3.0.3",
                  "info": { "title": "No Id API", "version": "1.0.0" },
                  "paths": {
                    "/books/{bookId}": {
                      "get": { "responses": { "200": { "description": "OK" } } }
                    }
                  }
                }
                """;

        List<OpenApiOperation> operations = importer.parseFromContent(spec).operations;

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).getOperationId()).isEqualTo("get_books_bookId");
    }
}
