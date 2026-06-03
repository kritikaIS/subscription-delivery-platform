package com.juiceplatform.config;

import com.juiceplatform.security.OnboardingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final OnboardingInterceptor onboardingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(onboardingInterceptor)
                // Apply ONLY to Customer Business APIs
                .addPathPatterns(
                        "/api/v1/customer/**",
                        "/api/v1/subscriptions",
                        "/api/v1/subscriptions/**",
                        "/api/v1/orders",
                        "/api/v1/orders/**",
                        "/api/v1/wallet",
                        "/api/v1/wallet/**"
                )
                // Explicitly EXCLUDE the onboarding API so users can actually onboard
                .excludePathPatterns(
                        "/api/v1/onboarding",
                        "/api/v1/onboarding/**"
                );
    }
}
