package com.juiceplatform.service;

import com.juiceplatform.dto.customer.CustomerProfileResponse;
import com.juiceplatform.dto.customer.UpdateAddressRequest;
import com.juiceplatform.dto.customer.UpdateAddressResponse;

import java.util.UUID;

public interface CustomerService {

    /**
     * Returns the authenticated customer's full profile including address and wallet summary.
     * API spec §2.3 — GET /api/v1/customer/me
     */
    CustomerProfileResponse getProfile(UUID customerId);

    /**
     * Updates the customer's delivery address immediately.
     * No cutoff rule applies (BR-ONB-03, BR-CUT-05).
     * Existing order address snapshots are NOT modified (BR-ONB-04).
     * API spec §2.2 — PUT /api/v1/customer/address
     */
    UpdateAddressResponse updateAddress(UUID customerId, UpdateAddressRequest request);
}
