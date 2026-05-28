package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.common.PagedResponse;
import com.juiceplatform.dto.common.PaginationMeta;
import com.juiceplatform.dto.wallet.LedgerEntryResponse;
import com.juiceplatform.dto.wallet.RechargeRequestBody;
import com.juiceplatform.dto.wallet.RechargeRequestResponse;
import com.juiceplatform.dto.wallet.WalletSummaryResponse;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.WalletRechargeRequestService;
import com.juiceplatform.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final WalletRechargeRequestService rechargeRequestService;

    @GetMapping
    public ResponseEntity<ApiResponse<WalletSummaryResponse>> getWalletSummary(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        WalletSummaryResponse response = walletService.getWalletSummary(authenticatedUser.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<PagedResponse<LedgerEntryResponse>>> getLedgerHistory(
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        Page<LedgerEntryResponse> page = walletService.getLedgerHistory(
                authenticatedUser.getUserId(), pageable);

        PagedResponse<LedgerEntryResponse> data = new PagedResponse<>(page.getContent());
        PaginationMeta meta = new PaginationMeta(page.getNumber(), page.getSize(), page.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(data, meta));
    }

    @PostMapping("/recharge-request")
    public ResponseEntity<ApiResponse<RechargeRequestResponse>> requestRecharge(
            @RequestBody(required = false) RechargeRequestBody body,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        RechargeRequestResponse response = rechargeRequestService.requestRecharge(
                authenticatedUser.getUserId(),
                body != null ? body : new RechargeRequestBody());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
