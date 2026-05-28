package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.dto.wallet.AdminCreditRequest;
import com.juiceplatform.dto.wallet.AdminCreditResponse;
import com.juiceplatform.dto.wallet.WalletSummaryResponse;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.WalletLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class WalletServiceTest extends AbstractIntegrationTest {

    @Autowired WalletService walletService;
    @Autowired TestDataFactory factory;
    @Autowired WalletLedgerRepository walletLedgerRepository;

    User customer;
    User admin;

    @BeforeEach
    void setUp() {
        customer = factory.createCustomer();
        admin = factory.createAdmin();
    }

    @Test
    void getWalletSummary_noEntries_returnsZeroBalance() {
        WalletSummaryResponse summary = walletService.getWalletSummary(customer.getId());

        assertThat(summary.getBalancePaise()).isZero();
        assertThat(summary.isLowBalanceWarning()).isTrue(); // 0 < 20000
        assertThat(summary.getLowBalanceThresholdPaise()).isEqualTo(20_000L);
    }

    @Test
    void getWalletSummary_afterCredit_returnsCorrectBalance() {
        factory.creditWallet(customer.getId(), 50000L, admin.getId());

        WalletSummaryResponse summary = walletService.getWalletSummary(customer.getId());

        assertThat(summary.getBalancePaise()).isEqualTo(50000L);
        assertThat(summary.isLowBalanceWarning()).isFalse(); // 50000 >= 20000
    }

    @Test
    void getWalletSummary_lowBalanceWarning_triggersBelow20000() {
        factory.creditWallet(customer.getId(), 15000L, admin.getId());

        WalletSummaryResponse summary = walletService.getWalletSummary(customer.getId());

        assertThat(summary.isLowBalanceWarning()).isTrue();
    }

    @Test
    void creditWallet_happyPath_insertsLedgerEntry() {
        AdminCreditRequest request = new AdminCreditRequest(50000L, "UPI payment ref TXN123");
        AdminCreditResponse response = walletService.creditWallet(customer.getId(), request, admin.getId());

        assertThat(response.getAmountPaise()).isEqualTo(50000L);
        assertThat(response.getNewBalancePaise()).isEqualTo(50000L);
        assertThat(response.getEntryType()).isEqualTo("CREDIT");
        assertThat(response.getSourceType()).isEqualTo("ADMIN_CREDIT");
        assertThat(response.getLedgerEntryId()).isNotNull();
    }

    @Test
    void creditWallet_multipleCredits_accumulatesBalance() {
        walletService.creditWallet(customer.getId(), new AdminCreditRequest(30000L, "first"), admin.getId());
        walletService.creditWallet(customer.getId(), new AdminCreditRequest(20000L, "second"), admin.getId());

        WalletSummaryResponse summary = walletService.getWalletSummary(customer.getId());
        assertThat(summary.getBalancePaise()).isEqualTo(50000L);
    }

    @Test
    void creditWallet_belowMinimum_throws400() {
        AdminCreditRequest request = new AdminCreditRequest(50L, "too small");

        assertThatThrownBy(() -> walletService.creditWallet(customer.getId(), request, admin.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void creditWallet_exactMinimum_succeeds() {
        AdminCreditRequest request = new AdminCreditRequest(100L, "minimum credit");
        AdminCreditResponse response = walletService.creditWallet(customer.getId(), request, admin.getId());

        assertThat(response.getAmountPaise()).isEqualTo(100L);
    }

    @Test
    void creditWallet_unknownCustomer_throws404() {
        AdminCreditRequest request = new AdminCreditRequest(10000L, "test");

        assertThatThrownBy(() -> walletService.creditWallet(UUID.randomUUID(), request, admin.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void getLedgerHistory_returnsNewestFirst() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        factory.creditWallet(customer.getId(), 5000L, admin.getId());

        var page = walletService.getLedgerHistory(customer.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        // Newest first — second credit (5000) should appear first
        assertThat(page.getContent().get(0).getAmountPaise()).isEqualTo(5000L);
        assertThat(page.getContent().get(1).getAmountPaise()).isEqualTo(10000L);
    }

    @Test
    void getLedgerHistory_emptyForNewCustomer() {
        var page = walletService.getLedgerHistory(customer.getId(), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isZero();
    }
}
