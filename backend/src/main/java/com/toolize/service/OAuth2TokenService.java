package com.toolize.service;

import com.toolize.domain.ApiAuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Obtains and caches OAuth2 access tokens for the "client_credentials" grant,
 * so that Toolize can call APIs protected by machine-to-machine OAuth2
 * without the user ever having to paste a token by hand.
 *
 * Tokens are cached per (token url, client id, scope) and refreshed a little
 * before they actually expire.
 */
@Service
public class OAuth2TokenService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2TokenService.class);
    private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofSeconds(30);

    private final RestClient restClient = RestClient.builder().build();
    private final ConcurrentHashMap<String, CachedToken> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    /**
     * Returns a valid access token for the given auth config, fetching (or
     * refreshing) it from the token endpoint if needed.
     *
     * Locking is per cache key (token url + client id + scope), not global:
     * refreshing a token for one API must never block a concurrent call to a
     * different API using a different OAuth2 provider.
     */
    public String getAccessToken(ApiAuthConfig auth) {
        String key = cacheKey(auth);
        CachedToken cached = cache.get(key);
        if (cached != null && cached.isValid()) {
            return cached.accessToken;
        }

        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            CachedToken recheck = cache.get(key);
            if (recheck != null && recheck.isValid()) {
                return recheck.accessToken;
            }
            CachedToken fresh = fetchToken(auth);
            cache.put(key, fresh);
            return fresh.accessToken;
        }
    }

    @SuppressWarnings("unchecked")
    private CachedToken fetchToken(ApiAuthConfig auth) {
        if (auth.getOauth2TokenUrl() == null || auth.getOauth2TokenUrl().isBlank()) {
            throw new IllegalStateException("OAuth2 token URL is not configured");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        if (auth.getOauth2Scope() != null && !auth.getOauth2Scope().isBlank()) {
            form.add("scope", auth.getOauth2Scope());
        }

        Map<String, Object> response = restClient.post()
                .uri(auth.getOauth2TokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(h -> h.setBasicAuth(
                        auth.getOauth2ClientId() != null ? auth.getOauth2ClientId() : "",
                        auth.getOauth2ClientSecret() != null ? auth.getOauth2ClientSecret() : ""))
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("Token endpoint did not return an access_token");
        }

        String accessToken = String.valueOf(response.get("access_token"));
        long expiresInSeconds = 3600;
        Object expiresIn = response.get("expires_in");
        if (expiresIn instanceof Number number) {
            expiresInSeconds = number.longValue();
        } else if (expiresIn != null) {
            try {
                expiresInSeconds = Long.parseLong(String.valueOf(expiresIn));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }

        Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds).minus(EXPIRY_SAFETY_MARGIN);
        log.debug("Fetched OAuth2 access token from {} (expires in {}s)", auth.getOauth2TokenUrl(), expiresInSeconds);
        return new CachedToken(accessToken, expiresAt);
    }

    private String cacheKey(ApiAuthConfig auth) {
        return auth.getOauth2TokenUrl() + "|" + auth.getOauth2ClientId() + "|" + auth.getOauth2Scope();
    }
}
