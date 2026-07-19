package com.toolize.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User-provided overrides for how a generated MCP tool describes itself and
 * its parameters to an LLM, layered on top of whatever the OpenAPI spec
 * itself provided. Kept separate from {@link OpenApiOperation} so re-imports
 * or spec updates never silently wipe out a user's wording.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCustomization {

    private String description;
    private Map<String, String> parameterDescriptions = new LinkedHashMap<>();

    public ToolCustomization() {
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getParameterDescriptions() {
        return parameterDescriptions;
    }

    public void setParameterDescriptions(Map<String, String> parameterDescriptions) {
        this.parameterDescriptions = parameterDescriptions != null ? parameterDescriptions : new LinkedHashMap<>();
    }

    public boolean isEmpty() {
        boolean noDescription = description == null || description.isBlank();
        boolean noParamDescriptions = parameterDescriptions == null || parameterDescriptions.isEmpty();
        return noDescription && noParamDescriptions;
    }
}
