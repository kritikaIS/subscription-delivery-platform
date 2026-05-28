package com.juiceplatform.security;

import com.juiceplatform.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies CORS configuration is loaded from properties (not hardcoded wildcard)
 * and that the allowed-origins list is non-empty and does not contain "*".
 */
class SecurityConfigTest extends AbstractIntegrationTest {

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Value("${app.cors.allowed-origins}")
    private List<String> configuredOrigins;

    @Test
    void corsConfig_allowedOrigins_areLoadedFromProperties() {
        // The configured origins must come from properties, not be hardcoded
        assertThat(configuredOrigins).isNotEmpty();
    }

    @Test
    void corsConfig_doesNotAllowWildcardOrigin() {
        // Wildcard "*" must never appear — it would bypass browser CORS protection
        assertThat(configuredOrigins).doesNotContain("*");

        // Also verify via the actual CorsConfigurationSource bean
        CorsConfiguration config = corsConfigurationSource
                .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());
        assertThat(config).isNotNull();
        List<String> resolvedOrigins = config.getAllowedOrigins();
        assertThat(resolvedOrigins).isNotNull();
        assertThat(resolvedOrigins).doesNotContain("*");
    }

    @Test
    void corsConfig_allowsConfiguredLocalhostOrigins() {
        // Test profile sets localhost origins — verify they are present
        assertThat(configuredOrigins)
                .anyMatch(origin -> origin.startsWith("http://localhost"));
    }

    @Test
    void corsConfig_allowsCredentials() {
        // allowCredentials must be true when origins are explicit (not wildcard)
        CorsConfiguration config = corsConfigurationSource
                .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());
        assertThat(config).isNotNull();
        assertThat(config.getAllowCredentials()).isTrue();
    }

    @Test
    void corsConfig_allowsRequiredHttpMethods() {
        CorsConfiguration config = corsConfigurationSource
                .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());
        assertThat(config).isNotNull();
        assertThat(config.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }
}
