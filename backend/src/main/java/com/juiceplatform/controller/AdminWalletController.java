package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.wallet.AdminCreditRequest;
import com.juiceplatform.dto.wallet.AdminCreditResponse;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
}
