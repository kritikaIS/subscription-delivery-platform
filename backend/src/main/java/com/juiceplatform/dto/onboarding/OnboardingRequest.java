package com.juiceplatform.dto.onboarding;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingRequest {

    @NotBlank
    @Size(max = 15)
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone must be 10-15 digits")
    private String phone;

    @NotNull
    @Valid
    private AddressRequest address;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressRequest {

        @NotBlank
        @Size(max = 255)
        private String line1;

        @Size(max = 255)
        private String line2;

        @NotBlank
        @Size(max = 100)
        private String city;

        @NotBlank
        @Size(max = 100)
        private String state;

        @NotBlank
        @Size(max = 10)
        @Pattern(regexp = "^[0-9]{5,10}$", message = "Pincode must be 5-10 digits")
        private String pincode;

        private String deliveryNotes;
    }
}
