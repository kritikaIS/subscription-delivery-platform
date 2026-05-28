package com.juiceplatform.security;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.controller.DevAuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that DevAuthController is NOT loaded when the "dev" profile is inactive.
 *
 * The test profile (@ActiveProfiles("test") in AbstractIntegrationTest) does NOT include "dev",
 * so DevAuthController — which is @Profile("dev") — must not be present in the context.
 *
 * This guards against the production risk of spring.profiles.active=dev being hardcoded,
 * which would expose unauthenticated token-generation endpoints.
 */
class DevProfileIsolationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void devAuthController_isNotLoadedInTestProfile() {
        // DevAuthController is @Profile("dev") — must be absent when profile is "test"
        boolean devControllerPresent = applicationContext.getBeanNamesForType(DevAuthController.class).length > 0;
        assertThat(devControllerPresent)
                .as("DevAuthController must NOT be loaded outside the 'dev' profile")
                .isFalse();
    }

    @Test
    void activeProfiles_doNotIncludeDev() {
        String[] activeProfiles = applicationContext.getEnvironment().getActiveProfiles();
        assertThat(activeProfiles)
                .as("The 'dev' profile must not be active in test/production contexts")
                .doesNotContain("dev");
    }
}
