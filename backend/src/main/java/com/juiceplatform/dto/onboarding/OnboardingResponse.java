package com.juiceplatform.dto.onboarding;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class OnboardingResponse {

    private UUID customerId;
    private String phone;
    private boolean onboardingComplete;
    private AddressResponse address;

    @Getter
    @Builder
    public static class AddressResponse {
        private UUID id;
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String pincode;
        private String deliveryNotes;
    }
}
