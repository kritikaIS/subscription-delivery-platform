package com.juiceplatform.service;

import com.juiceplatform.dto.wallet.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WalletService {

    WalletSummaryResponse getWalletSummary(UUID customerId);

    Page<LedgerEntryResponse> getLedgerHistory(UUID customerId, Pageable pageable);

    AdminCreditResponse creditWallet(UUID customerId, AdminCreditRequest request, UUID adminId);

    long getCurrentBalance(UUID customerId);

    LedgerEntryResponse adjustWallet(UUID customerId, AdminAdjustWalletRequest request, UUID adminId);
    LedgerEntryResponse setBalance(UUID customerId, AdminSetBalanceRequest request, UUID adminId);
}
