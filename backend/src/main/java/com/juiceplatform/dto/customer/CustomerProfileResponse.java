package com.juiceplatform.dto.customer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for GET /api/v1/customer/me (API spec §2.3).
 */
@Getter
@Builder
public class CustomerProfileResponse {

    private UUID id;
    private String name;
    private String email;
    private String phone;
    private boolean onboardingComplete;

    /** Null when onboarding is not yet complete (API spec §2.3 note). */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private AddressDto address;

    private WalletDto wallet;
    private OffsetDateTime createdAt;

    @Getter
    @Builder
    public static class AddressDto {
        private UUID id;
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String pincode;
        private String deliveryNotes;
    }

    @Getter
    @Builder
    public static class WalletDto {
        private long balancePaise;
        private boolean lowBalanceWarning;
        private long lowBalanceThresholdPaise;
    }
}
