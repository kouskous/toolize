package com.toolize.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record AuthStatus(boolean authenticated, String username) {
    }

    /**
     * Actual login/logout are handled by Spring Security's own filters
     * (see SecurityConfig) - this endpoint only lets the SPA ask "am I
     * logged in?" on load/navigation without triggering a 401 itself.
     */
    @GetMapping("/status")
    public AuthStatus status(Authentication authentication) {
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return new AuthStatus(authenticated, authenticated ? authentication.getName() : null);
    }
}
