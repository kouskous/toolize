package com.toolize.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Serves the compiled Vue application from classpath:/static for
 * every route that is not /api/** or /mcp/**, so that client-side
 * routing (Vue Router in history mode) works on full page reloads.
 */
@Configuration
public class SpaRoutingConfig {

    @Bean
    public RouterFunction<ServerResponse> spaFallbackRouter() {
        return RouterFunctions.route()
                .GET("/**", request -> {
                    String path = request.path();
                    if (path.startsWith("/api") || path.startsWith("/mcp")) {
                        return ServerResponse.notFound().build();
                    }
                    if (path.contains(".") && !path.endsWith(".html")) {
                        // let static resource handler serve real asset files (js, css, svg...)
                        return ServerResponse.notFound().build();
                    }
                    Resource index = new ClassPathResource("static/index.html");
                    return ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(index);
                })
                .build();
    }
}
