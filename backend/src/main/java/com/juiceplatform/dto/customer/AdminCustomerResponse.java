package com.juiceplatform.dto.customer;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class AdminCustomerResponse {

    private UUID id;
    private String name;
    private String phone;
    private String email;
    private Boolean isActive;
    private Boolean onboardingComplete;
    private Long walletBalancePaise;
    private OffsetDateTime createdAt;
}
