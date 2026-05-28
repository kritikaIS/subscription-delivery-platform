package com.juiceplatform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI juicePlatformOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Juice Subscription & Delivery Platform API")
                        .version("v1")
                        .description("""
                                REST API for the Juice Subscription and Delivery Platform.

                                Supports two roles:
                                - Customer: Google OAuth login, subscription management, wallet, orders.
                                - Admin: Product management, order operations, wallet credits, delivery sheets, scheduler control.

                                All monetary values are in paise (1 INR = 100 paise).
                                All timestamps use Asia/Kolkata (IST) timezone.
                                All resource IDs are UUIDs.
                                """)
                )
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste your JWT access token here (without the 'Bearer ' prefix)")
                        )
                );
    }
}
