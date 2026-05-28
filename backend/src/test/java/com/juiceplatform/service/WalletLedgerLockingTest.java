package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.dto.wallet.AdminCreditRequest;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.User;
import com.juiceplatform.entity.WalletLedger;
import com.juiceplatform.repository.WalletLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for wallet ledger locking behavior (db-schema §6.1).
 *
 * The locking query (findTopByCustomerIdForUpdate) must:
 * - Return the correct latest balance
 * - Be callable within a transaction
 * - Produce correct running_balance_paise after each mutation
 *
 * True concurrent lock contention cannot be tested in a single-threaded @Transactional test
 * (both threads would share the same transaction). These tests verify the correctness of
 * balance computation under the locking path, which is the observable contract.
 */
@Transactional
class WalletLedgerLockingTest extends AbstractIntegrationTest {

    @Autowired WalletService walletService;
    @Autowired DeliveryService deliveryService;
    @Autowired OrderCorrectionService correctionService;
    @Autowired TestDataFactory factory;
    @Autowired WalletLedgerRepository walletLedgerRepository;

    User customer;
    User admin;
    Product product;

    @BeforeEach
    void setUp() {
        customer = factory.createCustomer();
        factory.createAddress(customer.getId());
        admin = factory.createAdmin();
        product = factory.createProduct(2500L);
    }

    // ─── findTopByCustomerIdForUpdate ────────────────────────────────────────

    @Test
    void findTopByCustomerIdForUpdate_noEntries_returnsEmpty() {
        Optional<WalletLedger> result =
                walletLedgerRepository.findTopByCustomerIdForUpdate(customer.getId());
        assertThat(result).isEmpty();
    }

    @Test
    void findTopByCustomerIdForUpdate_returnsLatestEntry() {
        factory.creditWallet(customer.getId(), 10_000L, admin.getId());
        factory.creditWallet(customer.getId(), 5_000L, admin.getId());

        Optional<WalletLedger> result =
                walletLedgerRepository.findTopByCustomerIdForUpdate(customer.getId());

        assertThat(result).isPresent();
        // Latest entry has running balance = 10000 + 5000 = 15000
        assertThat(result.get().getRunningBalancePaise()).isEqualTo(15_000L);
    }

    // ─── Admin credit uses locking path ─────────────────────────────────────

    @Test
    void creditWallet_usesLockingPath_producesCorrectRunningBalance() {
        // First credit
        walletService.creditWallet(customer.getId(), new AdminCreditRequest(30_000L, "first"), admin.getId());
        // Second credit — must read locked balance = 30000, produce 50000
        walletService.creditWallet(customer.getId(), new AdminCreditRequest(20_000L, "second"), admin.getId());

        WalletLedger latest = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElseThrow();
        assertThat(latest.getRunningBalancePaise()).isEqualTo(50_000L);
    }

    @Test
    void creditWallet_firstCreditOnEmptyWallet_startsFromZero() {
        walletService.creditWallet(customer.getId(), new AdminCreditRequest(10_000L, "initial"), admin.getId());

        WalletLedger entry = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElseThrow();
        assertThat(entry.getRunningBalancePaise()).isEqualTo(10_000L);
        assertThat(entry.getAmountPaise()).isEqualTo(10_000L);
        assertThat(entry.getEntryType()).isEqualTo(WalletLedger.EntryType.CREDIT);
    }

    // ─── Delivery debit uses locking path ───────────────────────────────────

    @Test
    void markDelivered_usesLockingPath_deductsCorrectly() {
        factory.creditWallet(customer.getId(), 10_000L, admin.getId());
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 2);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 2, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        deliveryService.markDelivered(order.getId(), admin.getId());

        WalletLedger debit = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElseThrow();
        // 10000 - 5000 = 5000
        assertThat(debit.getRunningBalancePaise()).isEqualTo(5_000L);
        assertThat(debit.getEntryType()).isEqualTo(WalletLedger.EntryType.DEBIT);
        assertThat(debit.getSourceType()).isEqualTo(WalletLedger.SourceType.DELIVERY_DEBIT);
    }

    // ─── Historical correction refund uses locking path ─────────────────────

    @Test
    void historicalCorrection_refund_usesLockingPath_restoresBalance() {
        factory.creditWallet(customer.getId(), 10_000L, admin.getId());
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 1, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        // Deliver: balance 10000 → 7500
        deliveryService.markDelivered(order.getId(), admin.getId());

        // Correct DELIVERED → SKIPPED with system error: balance 7500 → 10000
        var request = new com.juiceplatform.dto.delivery.OrderCorrectionRequest(
                "SKIPPED", "DAMAGED", true, null, null);
        correctionService.correctOrder(order.getId(), request, admin.getId());

        WalletLedger refund = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElseThrow();
        assertThat(refund.getRunningBalancePaise()).isEqualTo(10_000L);
        assertThat(refund.getEntryType()).isEqualTo(WalletLedger.EntryType.REFUND);
        assertThat(refund.getSourceType()).isEqualTo(WalletLedger.SourceType.HISTORICAL_CORRECTION);
    }

    // ─── Historical correction debit uses locking path ──────────────────────

    @Test
    void historicalCorrection_debit_usesLockingPath_allowsNegativeBalance() {
        // No wallet credit — balance = 0
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 1, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        // Skip first (no debit)
        deliveryService.markSkipped(order.getId(), "DAMAGED", admin.getId());

        // Correct SKIPPED → DELIVERED: balance 0 → -2500 (negative permitted per BR-HIS-03)
        var request = new com.juiceplatform.dto.delivery.OrderCorrectionRequest(
                "DELIVERED", null, null, null, null);
        correctionService.correctOrder(order.getId(), request, admin.getId());

        WalletLedger debit = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElseThrow();
        assertThat(debit.getRunningBalancePaise()).isEqualTo(-2_500L);
        assertThat(debit.getEntryType()).isEqualTo(WalletLedger.EntryType.DEBIT);
        assertThat(debit.getSourceType()).isEqualTo(WalletLedger.SourceType.HISTORICAL_CORRECTION_DEBIT);
    }

    // ─── Ledger append-only invariant ────────────────────────────────────────

    @Test
    void ledger_isAppendOnly_oldEntriesNeverMutated() {
        factory.creditWallet(customer.getId(), 10_000L, admin.getId());
        WalletLedger firstEntry = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElseThrow();
        long firstEntryId = 0; // capture the UUID for comparison
        java.util.UUID firstEntryUuid = firstEntry.getId();
        long firstRunningBalance = firstEntry.getRunningBalancePaise();

        // Add a second credit
        walletService.creditWallet(customer.getId(), new AdminCreditRequest(5_000L, "second"), admin.getId());

        // Re-read the first entry — it must be unchanged
        WalletLedger reread = walletLedgerRepository.findById(firstEntryUuid).orElseThrow();
        assertThat(reread.getRunningBalancePaise()).isEqualTo(firstRunningBalance);
        assertThat(reread.getAmountPaise()).isEqualTo(10_000L);

        // Total entries = 2 (append-only)
        long count = walletLedgerRepository
                .findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId(),
                        org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements();
        assertThat(count).isEqualTo(2);
    }
}
