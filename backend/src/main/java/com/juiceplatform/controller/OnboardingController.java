package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.onboarding.OnboardingRequest;
import com.juiceplatform.dto.onboarding.OnboardingResponse;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping
    public ResponseEntity<ApiResponse<OnboardingResponse>> completeOnboarding(
            @RequestBody @Valid OnboardingRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        OnboardingResponse response = onboardingService.completeOnboarding(
                authenticatedUser.getUserId(), request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
