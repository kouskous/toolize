package com.toolize.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end regression test for a real bug: any wrong Basic Auth
 * credentials against /api/** or /mcp/** used to return HTTP 200 (with the
 * SPA's index.html) instead of 401, because SpaRoutingConfig's fallback
 * router matched Tomcat's internal /error dispatch and served the SPA shell
 * over it. This must run against a real embedded server (not MockMvc, which
 * never exercises the container's own error-page dispatch) to actually catch
 * a regression here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient restClient() {
        return RestClient.create("http://localhost:" + port);
    }

    private HttpStatusCode get(String path, String username, String password) {
        RestClient.RequestHeadersSpec<?> request = restClient().get().uri(path);
        if (username != null) {
            request = request.headers(h -> h.setBasicAuth(username, password));
        }
        return request.exchange((req, response) -> response.getStatusCode(), false);
    }

    @Test
    void rejectsRequestsWithNoCredentials() {
        assertThat(get("/api/projects", null, null).value()).isEqualTo(401);
    }

    @Test
    void rejectsWrongPasswordInsteadOfSilentlyReturningOk() {
        assertThat(get("/api/projects", "admin", "definitely-the-wrong-password").value()).isEqualTo(401);
    }

    @Test
    void rejectsWrongCredentialsAgainstMcpToo() {
        HttpStatusCode status = restClient().post().uri("/mcp")
                .headers(h -> {
                    h.setBasicAuth("admin", "definitely-the-wrong-password");
                    h.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")
                .exchange((req, response) -> response.getStatusCode(), false);

        assertThat(status.value()).isEqualTo(401);
    }

    @Test
    void acceptsTheConfiguredCredentials() {
        assertThat(get("/api/projects", "admin", "test-admin-password").value()).isEqualTo(200);
    }

    @Test
    void leavesTheSpaShellItselfPubliclyReachable() {
        assertThat(get("/", null, null).value()).isEqualTo(200);
    }
}
