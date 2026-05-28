package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.customer.CustomerProfileResponse;
import com.juiceplatform.dto.customer.UpdateAddressRequest;
import com.juiceplatform.dto.customer.UpdateAddressResponse;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer profile and address management endpoints.
 * All endpoints require CUSTOMER role (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /**
     * GET /api/v1/customer/me
     * Returns the authenticated customer's profile, address, and wallet summary.
     * API spec §2.3.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> getProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        CustomerProfileResponse response = customerService.getProfile(
                authenticatedUser.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PUT /api/v1/customer/address
     * Updates the customer's delivery address immediately.
     * No cutoff rule applies (BR-ONB-03, BR-CUT-05).
     * API spec §2.2.
     */
    @PutMapping("/address")
    public ResponseEntity<ApiResponse<UpdateAddressResponse>> updateAddress(
            @RequestBody @Valid UpdateAddressRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        UpdateAddressResponse response = customerService.updateAddress(
                authenticatedUser.getUserId(), request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
