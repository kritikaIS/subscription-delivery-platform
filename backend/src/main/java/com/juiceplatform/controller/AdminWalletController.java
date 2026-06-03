package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.common.PaginationMeta;
import com.juiceplatform.dto.wallet.*;
import com.juiceplatform.entity.WalletLedger;
import com.juiceplatform.repository.WalletLedgerRepository;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/customers")
@RequiredArgsConstructor
public class AdminWalletController {

    private final WalletService walletService;
    private final WalletLedgerRepository walletLedgerRepository;

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
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/wallet/set-balance")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> setBalance(
            @PathVariable("id") UUID customerId,
            @Valid @RequestBody AdminSetBalanceRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedAdmin) {

        LedgerEntryResponse response = walletService.setBalance(customerId, request, authenticatedAdmin.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{customerId}/wallet/ledger")
    public ResponseEntity<ApiResponse<List<LedgerEntryResponse>>> getCustomerLedger(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<WalletLedger> ledgerPage = walletLedgerRepository
                .findByCustomerIdOrderByCreatedAtDescIdDesc(customerId, pageable);

        List<LedgerEntryResponse> dtoList = ledgerPage.getContent().stream()
                .map(entry -> LedgerEntryResponse.builder()
                        .id(entry.getId())
                        .entryType(entry.getEntryType().name())
                        .sourceType(entry.getSourceType().name())
                        .amountPaise(entry.getAmountPaise())
                        .balanceAfterPaise(entry.getRunningBalancePaise())
                        .description(entry.getDescription())
                        .createdAt(entry.getCreatedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(ApiResponse.success(
                dtoList,
                new PaginationMeta(ledgerPage.getNumber(), ledgerPage.getSize(), ledgerPage.getTotalElements())
        ));
    }
}