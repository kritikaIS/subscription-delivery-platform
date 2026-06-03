package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.common.PaginationMeta;
import com.juiceplatform.dto.customer.AdminCustomerResponse;
import com.juiceplatform.dto.customer.CustomerActivationRequest;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.AdminCustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints for customers.
 * Note: wallet write operations (credit, adjust, set-balance, ledger) are in AdminWalletController
 * which also shares the /api/v1/admin/customers prefix.
 */
@RestController
@RequestMapping("/api/v1/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final AdminCustomerService adminCustomerService;

    // --- Read Endpoints ---

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminCustomerResponse>>> listCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminCustomerResponse> customerPage = adminCustomerService.getAllCustomers(pageable);

        return ResponseEntity.ok(ApiResponse.success(
                customerPage.getContent(),
                new PaginationMeta(customerPage.getNumber(), customerPage.getSize(), customerPage.getTotalElements())
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminCustomerResponse>> getCustomerDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminCustomerService.getCustomerDetail(id)));
    }

    // --- Write Endpoints ---

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<AdminCustomerResponse>> deactivateCustomer(
            @PathVariable UUID id,
            @RequestBody(required = false) CustomerActivationRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {

        String reason = (request != null && request.getReason() != null) ? request.getReason() : "No reason provided";
        AdminCustomerResponse response = adminCustomerService.deactivateCustomer(id, reason, admin.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<ApiResponse<AdminCustomerResponse>> reactivateCustomer(
            @PathVariable UUID id,
            @RequestBody(required = false) CustomerActivationRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {

        String reason = (request != null && request.getReason() != null) ? request.getReason() : "No reason provided";
        AdminCustomerResponse response = adminCustomerService.reactivateCustomer(id, reason, admin.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
