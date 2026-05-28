package com.juiceplatform.service;

import com.juiceplatform.dto.wallet.AdminCreditRequest;
import com.juiceplatform.dto.wallet.AdminCreditResponse;
import com.juiceplatform.dto.wallet.LedgerEntryResponse;
import com.juiceplatform.dto.wallet.WalletSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WalletService {

    WalletSummaryResponse getWalletSummary(UUID customerId);

    Page<LedgerEntryResponse> getLedgerHistory(UUID customerId, Pageable pageable);

    AdminCreditResponse creditWallet(UUID customerId, AdminCreditRequest request, UUID adminId);

    long getCurrentBalance(UUID customerId);
}
