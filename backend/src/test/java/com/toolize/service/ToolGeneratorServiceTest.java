package com.toolize.service;

import com.toolize.domain.McpTool;
import com.toolize.domain.OpenApiOperation;
import com.toolize.domain.ToolCustomization;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolGeneratorServiceTest {

    private final ToolGeneratorService generator = new ToolGeneratorService();

    private OpenApiOperation operation(String operationId, String summary, String description,
                                        List<OpenApiOperation.OpenApiParameter> parameters) {
        return new OpenApiOperation(operationId, "GET", "/books", summary, description, parameters, null);
    }

    @Test
    void fallsBackToSummaryWhenNoDescriptionOrCustomizationIsSet() {
        OpenApiOperation op = operation("listBooks", "List all books", null, List.of());

        List<McpTool> tools = generator.generateTools("proj", "https://api.example.com", List.of(op), Map.of());

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).getDescription()).isEqualTo("List all books");
    }

    @Test
    void prefersSpecDescriptionOverSummary() {
        OpenApiOperation op = operation("listBooks", "List all books", "Returns every book in the catalog.", List.of());

        List<McpTool> tools = generator.generateTools("proj", "https://api.example.com", List.of(op), Map.of());

        assertThat(tools.get(0).getDescription()).isEqualTo("Returns every book in the catalog.");
    }

    @Test
    void customizationTakesPrecedenceOverSpecDescriptionAndSummary() {
        OpenApiOperation op = operation("listBooks", "List all books", "Returns every book in the catalog.", List.of());
        ToolCustomization customization = new ToolCustomization();
        customization.setDescription("Use this to search the catalog before recommending a book to the user.");

        List<McpTool> tools = generator.generateTools("proj", "https://api.example.com", List.of(op),
                Map.of("listBooks", customization));

        assertThat(tools.get(0).getDescription())
                .isEqualTo("Use this to search the catalog before recommending a book to the user.");
    }

    @Test
    void parameterDescriptionCustomizationOverridesTheSpecOne() {
        OpenApiOperation.OpenApiParameter genre = new OpenApiOperation.OpenApiParameter(
                "genre", "query", false, "string", "Filter books by genre");
        OpenApiOperation op = operation("listBooks", "List all books", null, List.of(genre));

        ToolCustomization customization = new ToolCustomization();
        customization.setParameterDescriptions(Map.of("genre", "One of: fiction, non-fiction, poetry."));

        List<McpTool> tools = generator.generateTools("proj", "https://api.example.com", List.of(op),
                Map.of("listBooks", customization));

        String paramDescription = tools.get(0).getInputSchema().path("properties").path("genre").path("description").asString();
        assertThat(paramDescription).isEqualTo("One of: fiction, non-fiction, poetry.");
    }

    @Test
    void sanitizesOperationIdIntoAValidToolName() {
        OpenApiOperation op = operation("list-books.v2", "List all books", null, List.of());

        List<McpTool> tools = generator.generateTools("proj", "https://api.example.com", List.of(op), Map.of());

        assertThat(tools.get(0).getName()).isEqualTo("list_books_v2");
    }
}
