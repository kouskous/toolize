package com.toolize.config;

import com.toolize.domain.AdminCredentialEntity;
import com.toolize.service.AdminCredentialJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Everything under /api and /mcp requires an authenticated session (or, for
 * non-browser MCP clients, HTTP Basic credentials) - the Vue SPA shell itself
 * stays publicly reachable so an unauthenticated visitor gets the login page
 * instead of a blank/broken screen, while all data and every MCP tool call
 * stay locked down server-side.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int GENERATED_PASSWORD_LENGTH = 20;

    @Value("${toolize.admin.username}")
    private String adminUsername;

    @Value("${toolize.admin.password}")
    private String configuredPassword;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * TOOLIZE_ADMIN_PASSWORD always wins when set (and is what keeps every
     * replica of a scaled deployment consistent). Otherwise - so that a
     * zero-config "docker run" doesn't ship a well-known default credential -
     * a random password is generated once, persisted in the shared database,
     * and printed to the startup log; it survives restarts and is shared by
     * every replica reading the same database.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder, AdminCredentialJpaRepository credentials) {
        String passwordHash = !configuredPassword.isBlank()
                ? passwordEncoder.encode(configuredPassword)
                : credentials.findById(adminUsername)
                        .map(AdminCredentialEntity::getPasswordHash)
                        .orElseGet(() -> generateAndPersistPassword(passwordEncoder, credentials));

        return new InMemoryUserDetailsManager(
                User.withUsername(adminUsername)
                        .password(passwordHash)
                        .roles("ADMIN")
                        .build());
    }

    private String generateAndPersistPassword(PasswordEncoder passwordEncoder, AdminCredentialJpaRepository credentials) {
        String rawPassword = generateRandomPassword();
        String hash = passwordEncoder.encode(rawPassword);
        credentials.save(new AdminCredentialEntity(adminUsername, hash, Instant.now()));

        log.warn("""

                ============================================================
                No TOOLIZE_ADMIN_PASSWORD was set - generated one instead.
                  Username: {}
                  Password: {}
                This is stored in the database and reused on every restart.
                Set TOOLIZE_ADMIN_PASSWORD yourself to use your own instead.
                ============================================================
                """, adminUsername, rawPassword);

        return hash;
    }

    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/status", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/**", "/mcp/**", "/v3/api-docs/**", "/swagger-ui/**").authenticated()
                        .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> response.setStatus(HttpStatus.OK.value()))
                        .failureHandler((request, response, exception) -> response.setStatus(HttpStatus.UNAUTHORIZED.value()))
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.OK.value()))
                )
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(e -> e
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getServletPath().startsWith("/api"))
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getServletPath().startsWith("/mcp"))
                );

        return http.build();
    }
}
