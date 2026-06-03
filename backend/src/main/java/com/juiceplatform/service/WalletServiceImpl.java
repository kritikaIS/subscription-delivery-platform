package com.juiceplatform.service;

import com.juiceplatform.dto.wallet.*;
import com.juiceplatform.entity.User;
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

    private static final long LOW_BALANCE_THRESHOLD_PAISE = 20_000L;

    private final WalletLedgerRepository walletLedgerRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

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
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Customer not found: " + customerId, HttpStatus.NOT_FOUND));

        if (request.getAmountPaise() < 100) {
            throw new BusinessException("INVALID_AMOUNT",
                    "Minimum wallet credit amount is ₹1 (100 paise)", HttpStatus.BAD_REQUEST);
        }

        long currentBalance = walletLedgerRepository.findTopByCustomerIdForUpdate(customerId)
                .map(WalletLedger::getRunningBalancePaise)
                .orElse(0L);
        long newBalance = currentBalance + request.getAmountPaise();

        WalletLedger entry = new WalletLedger();
        entry.setCustomerId(customerId);
        entry.setEntryType(WalletLedger.EntryType.CREDIT);
        entry.setSourceType(WalletLedger.SourceType.ADMIN_CREDIT);
        entry.setAmountPaise(request.getAmountPaise());
        entry.setRunningBalancePaise(newBalance);
        entry.setDescription(request.getNotes() != null ? request.getNotes() : "Wallet top-up by admin");
        entry.setCreatedByUserId(adminId);
        entry = walletLedgerRepository.save(entry);

        auditLogService.log("BALANCE_CREDIT", "customer", customerId.toString(),
                null,
                java.util.Map.of("amountPaise", request.getAmountPaise(),
                        "newBalancePaise", newBalance,
                        "ledgerEntryId", entry.getId().toString()),
                adminId, request.getNotes());

        notificationService.notifyWalletCredited(customerId, customer.getName(), request.getAmountPaise(), newBalance);

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
    public long getCurrentBalance(UUID customerId) {
        return walletLedgerRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId)
                .map(WalletLedger::getRunningBalancePaise)
                .orElse(0L);
    }

    @Override
    @Transactional
    public LedgerEntryResponse adjustWallet(UUID customerId, AdminAdjustWalletRequest request, UUID adminId) {
        userRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Customer not found: " + customerId, HttpStatus.NOT_FOUND));

        if (request.amountPaise() == 0) {
            throw new BusinessException("INVALID_AMOUNT", "Adjustment amount cannot be zero", HttpStatus.BAD_REQUEST);
        }

        long currentBalance = walletLedgerRepository.findTopByCustomerIdForUpdate(customerId)
                .map(WalletLedger::getRunningBalancePaise)
                .orElse(0L);

        WalletLedger.EntryType entryType;
        WalletLedger.SourceType sourceType;
        long absoluteAmount = Math.abs(request.amountPaise());

        if (request.amountPaise() > 0) {
            entryType = WalletLedger.EntryType.CREDIT;
            sourceType = WalletLedger.SourceType.ADMIN_CREDIT;
        } else {
            entryType = WalletLedger.EntryType.DEBIT;
            sourceType = WalletLedger.SourceType.MANUAL_DEBIT;
        }

        long newBalance = currentBalance + request.amountPaise();

        WalletLedger entry = new WalletLedger();
        entry.setCustomerId(customerId);
        entry.setAmountPaise(absoluteAmount);
        entry.setRunningBalancePaise(newBalance);
        entry.setEntryType(entryType);
        entry.setSourceType(sourceType);
        entry.setDescription("Admin Adjustment: " + request.reason());
        entry.setCreatedByUserId(adminId);

        entry = walletLedgerRepository.save(entry);

        auditLogService.log("WALLET_ADJUST", "customer", customerId.toString(),
                java.util.Map.of("oldBalancePaise", currentBalance),
                java.util.Map.of("newBalancePaise", newBalance,
                        "amountAdjustedPaise", request.amountPaise(),
                        "ledgerEntryId", entry.getId().toString()),
                adminId, request.reason());

        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .entryType(entry.getEntryType().name())
                .sourceType(entry.getSourceType().name())
                .amountPaise(entry.getAmountPaise())
                .balanceAfterPaise(entry.getRunningBalancePaise())
                .description(entry.getDescription())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public LedgerEntryResponse setBalance(UUID customerId, AdminSetBalanceRequest request, UUID adminId) {
        long currentBalance = walletLedgerRepository.findTopByCustomerIdForUpdate(customerId)
                .map(WalletLedger::getRunningBalancePaise)
                .orElse(0L);

        long offset = request.targetBalancePaise() - currentBalance;

        if (offset == 0) {
            throw new BusinessException("INVALID_REQUEST", "Wallet is already at the target balance.", HttpStatus.BAD_REQUEST);
        }

        AdminAdjustWalletRequest adjustRequest = new AdminAdjustWalletRequest(offset, request.reason());
        return this.adjustWallet(customerId, adjustRequest, adminId);
    }
}