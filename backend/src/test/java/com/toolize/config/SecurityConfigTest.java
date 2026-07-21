package com.toolize.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for a real security bug: Toolize used to ship (and default
 * to) a well-known admin/admin credential. It must now refuse to start
 * without an explicitly configured password instead of falling back to
 * anything - these tests exercise that bean-creation logic directly, without
 * needing a full Spring context.
 */
class SecurityConfigTest {

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void refusesToStartWhenNoPasswordIsConfigured() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "adminUsername", "admin");
        ReflectionTestUtils.setField(config, "configuredPassword", "");

        assertThatThrownBy(() -> config.userDetailsService(passwordEncoder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOOLIZE_ADMIN_PASSWORD");
    }

    @Test
    void buildsAUserWhenAPasswordIsConfigured() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "adminUsername", "admin");
        ReflectionTestUtils.setField(config, "configuredPassword", "change-me");

        UserDetailsService userDetailsService = config.userDetailsService(passwordEncoder);

        var user = userDetailsService.loadUserByUsername("admin");
        assertThat(user.getUsername()).isEqualTo("admin");
        assertThat(passwordEncoder.matches("change-me", user.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", user.getPassword())).isFalse();
    }
}
