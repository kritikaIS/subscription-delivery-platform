package com.juiceplatform.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request DTO for PUT /api/v1/customer/address (API spec §2.2).
 * Validation mirrors OnboardingRequest.AddressRequest for consistency.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAddressRequest {

    @NotBlank(message = "line1 is required")
    @Size(max = 255)
    private String line1;

    @Size(max = 255)
    private String line2;

    @NotBlank(message = "city is required")
    @Size(max = 100)
    private String city;

    @NotBlank(message = "state is required")
    @Size(max = 100)
    private String state;

    @NotBlank(message = "pincode is required")
    @Size(max = 10)
    @Pattern(regexp = "^[0-9]{5,10}$", message = "Pincode must be 5-10 digits")
    private String pincode;

    private String deliveryNotes;
}
