package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.wallet.*;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/customers")
@RequiredArgsConstructor
public class AdminWalletController {

    private final WalletService walletService;

    @PostMapping("/{customerId}/wallet/credit")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AdminCreditResponse>> creditWallet(
            @PathVariable UUID customerId,
            @RequestBody @Valid AdminCreditRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        AdminCreditResponse response = walletService.creditWallet(
                customerId, request, authenticatedUser.getUserId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/wallet/adjust")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> adjustWallet(
            @PathVariable("id") UUID customerId,
            @Valid @RequestBody AdminAdjustWalletRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedAdmin) {

        LedgerEntryResponse response = walletService.adjustWallet(customerId, request, authenticatedAdmin.getUserId());
        // FIX: Removed the String message to match your ApiResponse wrapper
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/wallet/set-balance")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> setBalance(
            @PathVariable("id") UUID customerId,
            @Valid @RequestBody AdminSetBalanceRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedAdmin) {

        LedgerEntryResponse response = walletService.setBalance(customerId, request, authenticatedAdmin.getUserId());
        // FIX: Removed the String message to match your ApiResponse wrapper
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}