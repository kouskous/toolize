package com.toolize.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Serves the compiled Vue application from classpath:/static for
 * every route that is not /api/**, /mcp/**, or an OpenAPI/Swagger UI
 * route, so that client-side routing (Vue Router in history mode)
 * works on full page reloads.
 *
 * The exclusion must happen at the route predicate level (not inside
 * the handler): once this router's predicate matches, whatever
 * ServerResponse the handler returns (even a 404) is terminal and
 * short-circuits the resource-handler mapping that actually serves
 * springdoc's swagger-ui assets, since RouterFunctionMapping is
 * consulted before the static resource HandlerMapping.
 */
@Configuration
public class SpaRoutingConfig {

    @Bean
    public RouterFunction<ServerResponse> spaFallbackRouter() {
        RequestPredicate spaRoute = RequestPredicates.GET("/**").and(request -> {
            String path = request.path();
            // /error must fall through to Boot's own error controller: this router
            // otherwise catches Tomcat's error dispatch (e.g. the 401 raised by a
            // failed Basic Auth attempt) and masks it behind a 200 + index.html,
            // silently turning every authentication failure into an apparent success.
            return !path.equals("/error") && !path.startsWith("/api") && !path.startsWith("/mcp")
                    && !path.startsWith("/v3/api-docs") && !path.startsWith("/swagger-ui")
                    && !path.startsWith("/webjars") && !path.startsWith("/assets");
        });

        return RouterFunctions.route()
                .GET(spaRoute, request -> {
                    String path = request.path();
                    if (path.contains(".") && !path.endsWith(".html")) {
                        // let static resource handler serve real asset files (js, css, svg...)
                        return ServerResponse.notFound().build();
                    }
                    Resource index = new ClassPathResource("static/index.html");
                    return ServerResponse.ok().contentType(MediaType.TEXT_HTML).body(index);
                })
                .build();
    }
}
