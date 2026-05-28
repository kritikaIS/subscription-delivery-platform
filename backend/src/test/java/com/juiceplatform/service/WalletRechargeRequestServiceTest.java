package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.dto.wallet.RechargeRequestBody;
import com.juiceplatform.dto.wallet.RechargeRequestResponse;
import com.juiceplatform.entity.RechargeRequestLog;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.AdminAuditLogRepository;
import com.juiceplatform.repository.RechargeRequestLogRepository;
import com.juiceplatform.repository.WalletLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class WalletRechargeRequestServiceTest extends AbstractIntegrationTest {

    @Autowired WalletRechargeRequestService rechargeRequestService;
    @Autowired WalletLedgerRepository walletLedgerRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired RechargeRequestLogRepository rechargeRequestLogRepository;
    @Autowired TestDataFactory factory;

    User customer;
    User admin;

    @BeforeEach
    void setUp() {
        customer = factory.createCustomer();
        admin = factory.createAdmin();
        // Ensure no stale rate limit record for this customer
        rechargeRequestLogRepository.deleteById(customer.getId());
    }

    @Test
    void rechargeRequest_success_returnsRequestedStatus() {
        RechargeRequestResponse response = rechargeRequestService.requestRecharge(
                customer.getId(), new RechargeRequestBody("Wallet balance low"));

        assertThat(response.getStatus()).isEqualTo("REQUESTED");
        assertThat(response.getRequestedAt()).isNotNull();
    }

    @Test
    void rechargeRequest_persistsRateLimitRecord_inDatabase() {
        rechargeRequestService.requestRecharge(customer.getId(), new RechargeRequestBody("test"));

        // Rate limit record persisted in DB
        assertThat(rechargeRequestLogRepository.findById(customer.getId())).isPresent();
        RechargeRequestLog log = rechargeRequestLogRepository.findById(customer.getId()).orElseThrow();
        assertThat(log.getLastRequestedAt()).isNotNull();
        assertThat(log.getLastRequestedAt()).isAfter(OffsetDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(1));
    }

    @Test
    void rechargeRequest_doesNotMutateWalletBalance() {
        long balanceBefore = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .map(e -> e.getRunningBalancePaise())
                .orElse(0L);

        rechargeRequestService.requestRecharge(customer.getId(), new RechargeRequestBody("test"));

        long balanceAfter = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .map(e -> e.getRunningBalancePaise())
                .orElse(0L);

        // BR-NOT-02: no wallet_ledger entry created
        assertThat(balanceAfter).isEqualTo(balanceBefore);
    }

    @Test
    void rechargeRequest_doesNotCreateAuditLogEntry() {
        long auditCountBefore = auditLogRepository.count();

        rechargeRequestService.requestRecharge(customer.getId(), new RechargeRequestBody("test"));

        long auditCountAfter = auditLogRepository.count();

        // BR-NOT-02: recharge-request does NOT create admin_audit_log entries
        assertThat(auditCountAfter).isEqualTo(auditCountBefore);
    }

    @Test
    void rechargeRequest_rateLimited_throws429OnSecondRequestWithinHour() {
        // First request succeeds
        rechargeRequestService.requestRecharge(customer.getId(), new RechargeRequestBody("first"));

        // Second request within 1 hour → 429
        assertThatThrownBy(() ->
                rechargeRequestService.requestRecharge(customer.getId(), new RechargeRequestBody("second")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void rechargeRequest_rateLimitExpired_allowsNewRequest() {
        // Simulate a request that happened 2 hours ago
        RechargeRequestLog oldLog = new RechargeRequestLog(
                customer.getId(),
                OffsetDateTime.now(ZoneId.of("Asia/Kolkata")).minusHours(2));
        rechargeRequestLogRepository.save(oldLog);

        // New request should succeed (rate limit window has passed)
        RechargeRequestResponse response = rechargeRequestService.requestRecharge(
                customer.getId(), new RechargeRequestBody("after window"));

        assertThat(response.getStatus()).isEqualTo("REQUESTED");
    }

    @Test
    void rechargeRequest_differentCustomers_notRateLimited() {
        User customer2 = factory.createCustomer();
        rechargeRequestLogRepository.deleteById(customer2.getId());

        RechargeRequestResponse r1 = rechargeRequestService.requestRecharge(
                customer.getId(), new RechargeRequestBody("c1"));
        RechargeRequestResponse r2 = rechargeRequestService.requestRecharge(
                customer2.getId(), new RechargeRequestBody("c2"));

        assertThat(r1.getStatus()).isEqualTo("REQUESTED");
        assertThat(r2.getStatus()).isEqualTo("REQUESTED");
    }
}
