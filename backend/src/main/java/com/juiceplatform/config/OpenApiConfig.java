package com.juiceplatform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI juicePlatformOpenAPI() {
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
                );
    }
}
