package com.toolize.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Authentication configuration used to call the external API represented
 * by an {@link ApiProject}. Applied to every outgoing request in
 * {@code RestExecutionService}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiAuthConfig {

    public enum AuthType {
        NONE,
        API_KEY,
        BEARER_TOKEN,
        BASIC_AUTH
    }

    public enum ApiKeyLocation {
        HEADER,
        QUERY
    }

    private AuthType type = AuthType.NONE;
    private String apiKeyName;
    private ApiKeyLocation apiKeyLocation = ApiKeyLocation.HEADER;
    private String apiKeyValue;
    private String bearerToken;
    private String basicUsername;
    private String basicPassword;

    public ApiAuthConfig() {
    }

    public AuthType getType() {
        return type;
    }

    public void setType(AuthType type) {
        this.type = type != null ? type : AuthType.NONE;
    }

    public String getApiKeyName() {
        return apiKeyName;
    }

    public void setApiKeyName(String apiKeyName) {
        this.apiKeyName = apiKeyName;
    }

    public ApiKeyLocation getApiKeyLocation() {
        return apiKeyLocation;
    }

    public void setApiKeyLocation(ApiKeyLocation apiKeyLocation) {
        this.apiKeyLocation = apiKeyLocation != null ? apiKeyLocation : ApiKeyLocation.HEADER;
    }

    public String getApiKeyValue() {
        return apiKeyValue;
    }

    public void setApiKeyValue(String apiKeyValue) {
        this.apiKeyValue = apiKeyValue;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public String getBasicUsername() {
        return basicUsername;
    }

    public void setBasicUsername(String basicUsername) {
        this.basicUsername = basicUsername;
    }

    public String getBasicPassword() {
        return basicPassword;
    }

    public void setBasicPassword(String basicPassword) {
        this.basicPassword = basicPassword;
    }
}