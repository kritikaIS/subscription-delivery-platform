package com.juiceplatform.dto.customer;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for PUT /api/v1/customer/address (API spec §2.2).
 */
@Getter
@Builder
public class UpdateAddressResponse {

    private UUID id;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String pincode;
    private String deliveryNotes;
    private OffsetDateTime updatedAt;
}
