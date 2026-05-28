package com.juiceplatform.service;

import com.juiceplatform.dto.wallet.AdminCreditRequest;
import com.juiceplatform.dto.wallet.AdminCreditResponse;
import com.juiceplatform.dto.wallet.LedgerEntryResponse;
import com.juiceplatform.dto.wallet.WalletSummaryResponse;
import com.juiceplatform.entity.WalletLedger;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.UserRepository;
import com.juiceplatform.repository.WalletLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    // Low balance threshold: ₹200 = 20,000 paise (BR-WAL-09)
    private static final long LOW_BALANCE_THRESHOLD_PAISE = 20_000L;

    private final WalletLedgerRepository walletLedgerRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public WalletSummaryResponse getWalletSummary(UUID customerId) {
        long balance = getCurrentBalance(customerId);

        return WalletSummaryResponse.builder()
                .balancePaise(balance)
                .lowBalanceWarning(balance < LOW_BALANCE_THRESHOLD_PAISE)
                .lowBalanceThresholdPaise(LOW_BALANCE_THRESHOLD_PAISE)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getLedgerHistory(UUID customerId, Pageable pageable) {
        return walletLedgerRepository
                .findByCustomerIdOrderByCreatedAtDescIdDesc(customerId, pageable)
                .map(entry -> LedgerEntryResponse.builder()
                        .id(entry.getId())
                        .entryType(entry.getEntryType().name())
                        .sourceType(entry.getSourceType().name())
                        .amountPaise(entry.getAmountPaise())
                        .balanceAfterPaise(entry.getRunningBalancePaise())
                        .description(entry.getDescription())
                        .createdAt(entry.getCreatedAt())
                        .build());
    }

    @Override
    @Transactional
    public AdminCreditResponse creditWallet(UUID customerId, AdminCreditRequest request, UUID adminId) {
        // Verify customer exists
        userRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Customer not found: " + customerId, HttpStatus.NOT_FOUND));

        // Minimum credit validation (BR-WAL-07)
        if (request.getAmountPaise() < 100) {
            throw new BusinessException("INVALID_AMOUNT",
                    "Minimum wallet credit amount is ₹1 (100 paise)", HttpStatus.BAD_REQUEST);
        }

        // Compute new running balance
        long currentBalance = getCurrentBalance(customerId);
        long newBalance = currentBalance + request.getAmountPaise();

        // Insert CREDIT ledger entry (BR-WAL-04)
        WalletLedger entry = new WalletLedger();
        entry.setCustomerId(customerId);
        entry.setEntryType(WalletLedger.EntryType.CREDIT);
        entry.setSourceType(WalletLedger.SourceType.ADMIN_CREDIT);
        entry.setAmountPaise(request.getAmountPaise());
        entry.setRunningBalancePaise(newBalance);
        entry.setDescription(request.getNotes() != null ? request.getNotes() : "Wallet top-up by admin");
        entry.setCreatedByUserId(adminId);
        entry = walletLedgerRepository.save(entry);

        // TODO: Audit log — action_type: BALANCE_CREDIT, target_entity: customer, acting_admin: adminId

        return AdminCreditResponse.builder()
                .ledgerEntryId(entry.getId())
                .entryType(entry.getEntryType().name())
                .sourceType(entry.getSourceType().name())
                .amountPaise(entry.getAmountPaise())
                .newBalancePaise(newBalance)
                .notes(request.getNotes())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public long getCurrentBalance(UUID customerId) {
        // Balance = running_balance_paise of the latest ledger row (BR-WAL-02)
        // Customers with no entries have balance = 0 (BR-WAL-13)
        return walletLedgerRepository.findLatestByCustomerId(customerId)
                .map(WalletLedger::getRunningBalancePaise)
                .orElse(0L);
    }
}
