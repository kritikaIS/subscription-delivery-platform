package com.juiceplatform.dto.auth;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CustomerLoginResponse {

    private String accessToken;
    private String refreshToken;
    private UUID customerId;
    private boolean onboardingComplete;
}
