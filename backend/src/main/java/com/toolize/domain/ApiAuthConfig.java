package com.toolize.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

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
        BASIC_AUTH,
        OAUTH2_CLIENT_CREDENTIALS
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

    // OAuth2 "client_credentials" grant: Toolize fetches and caches its own
    // access token, refreshing it before every call once it expires.
    private String oauth2TokenUrl;
    private String oauth2ClientId;
    private String oauth2ClientSecret;
    private String oauth2Scope;

    // Extra static headers sent on every request, on top of whichever auth
    // mechanism above is active (e.g. a tenant id header some APIs require
    // alongside a bearer token).
    private Map<String, String> extraHeaders = new LinkedHashMap<>();

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

    public String getOauth2TokenUrl() {
        return oauth2TokenUrl;
    }

    public void setOauth2TokenUrl(String oauth2TokenUrl) {
        this.oauth2TokenUrl = oauth2TokenUrl;
    }

    public String getOauth2ClientId() {
        return oauth2ClientId;
    }

    public void setOauth2ClientId(String oauth2ClientId) {
        this.oauth2ClientId = oauth2ClientId;
    }

    public String getOauth2ClientSecret() {
        return oauth2ClientSecret;
    }

    public void setOauth2ClientSecret(String oauth2ClientSecret) {
        this.oauth2ClientSecret = oauth2ClientSecret;
    }

    public String getOauth2Scope() {
        return oauth2Scope;
    }

    public void setOauth2Scope(String oauth2Scope) {
        this.oauth2Scope = oauth2Scope;
    }

    public Map<String, String> getExtraHeaders() {
        return extraHeaders;
    }

    public void setExtraHeaders(Map<String, String> extraHeaders) {
        this.extraHeaders = extraHeaders != null ? extraHeaders : new LinkedHashMap<>();
    }
}