package com.juiceplatform.service;

import com.juiceplatform.dto.onboarding.OnboardingRequest;
import com.juiceplatform.dto.onboarding.OnboardingResponse;

import java.util.UUID;

public interface OnboardingService {

    OnboardingResponse completeOnboarding(UUID customerId, OnboardingRequest request);
}
