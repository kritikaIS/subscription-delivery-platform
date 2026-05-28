package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.entity.AdminAuditLog;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.User;
import com.juiceplatform.repository.AdminAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AuditLogServiceTest extends AbstractIntegrationTest {

    @Autowired AuditLogService auditLogService;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired WalletService walletService;
    @Autowired DeliveryService deliveryService;
    @Autowired TestDataFactory factory;

    User admin;
    User customer;
    Product product;

    @BeforeEach
    void setUp() {
        admin = factory.createAdmin();
        customer = factory.createCustomer();
        factory.createAddress(customer.getId());
        product = factory.createProduct(2500L);
    }

    @Test
    void auditLog_directWrite_isPersistedAndImmutable() {
        auditLogService.log("BALANCE_CREDIT", "customer", customer.getId().toString(),
                null, Map.of("amountPaise", 50000L), admin.getId(), "Test credit");

        Page<AdminAuditLog> logs = auditLogRepository
                .findByTargetEntityOrderByCreatedAtDesc("customer", PageRequest.of(0, 10));

        assertThat(logs.getTotalElements()).isEqualTo(1);
        AdminAuditLog entry = logs.getContent().get(0);
        assertThat(entry.getActionType()).isEqualTo("BALANCE_CREDIT");
        assertThat(entry.getTargetEntity()).isEqualTo("customer");
        assertThat(entry.getTargetId()).isEqualTo(customer.getId().toString());
        assertThat(entry.getActingAdmin()).isEqualTo(admin.getId());
        assertThat(entry.getNotes()).isEqualTo("Test credit");
        assertThat(entry.getOldValue()).isNull();
        assertThat(entry.getNewValue()).contains("50000");
        assertThat(entry.getCreatedAt()).isNotNull();
    }

    @Test
    void walletCredit_createsWalletLedgerRowAndAuditLogRow() {
        var request = new com.juiceplatform.dto.wallet.AdminCreditRequest(50000L, "UPI payment ref TXN123");
        walletService.creditWallet(customer.getId(), request, admin.getId());

        // Assert wallet ledger row exists
        var ledgerPage = walletService.getLedgerHistory(customer.getId(), PageRequest.of(0, 10));
        assertThat(ledgerPage.getTotalElements()).isEqualTo(1);
        assertThat(ledgerPage.getContent().get(0).getAmountPaise()).isEqualTo(50000L);
        assertThat(ledgerPage.getContent().get(0).getEntryType()).isEqualTo("CREDIT");
        assertThat(ledgerPage.getContent().get(0).getSourceType()).isEqualTo("ADMIN_CREDIT");

        // Assert admin_audit_log row exists
        Page<AdminAuditLog> logs = auditLogRepository
                .findByTargetEntityOrderByCreatedAtDesc("customer", PageRequest.of(0, 10));
        assertThat(logs.getTotalElements()).isEqualTo(1);

        AdminAuditLog auditEntry = logs.getContent().get(0);
        assertThat(auditEntry.getActionType()).isEqualTo("BALANCE_CREDIT");
        assertThat(auditEntry.getTargetEntity()).isEqualTo("customer");
        assertThat(auditEntry.getTargetId()).isEqualTo(customer.getId().toString());
        assertThat(auditEntry.getActingAdmin()).isEqualTo(admin.getId());
        assertThat(auditEntry.getOldValue()).isNull();
        assertThat(auditEntry.getNewValue()).contains("50000");
        assertThat(auditEntry.getCreatedAt()).isNotNull();
    }

    @Test
    void markDelivered_createsAuditLog() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 1, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        deliveryService.markDelivered(order.getId(), admin.getId());

        Page<AdminAuditLog> logs = auditLogRepository
                .findByTargetEntityOrderByCreatedAtDesc("order", PageRequest.of(0, 10));

        assertThat(logs.getTotalElements()).isEqualTo(1);
        assertThat(logs.getContent().get(0).getActionType()).isEqualTo("ORDER_OVERRIDE");
        assertThat(logs.getContent().get(0).getTargetId()).isEqualTo(order.getId().toString());
    }

    @Test
    void markSkipped_createsAuditLog() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 1, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        deliveryService.markSkipped(order.getId(), "DAMAGED", admin.getId());

        Page<AdminAuditLog> logs = auditLogRepository
                .findByTargetEntityOrderByCreatedAtDesc("order", PageRequest.of(0, 10));

        assertThat(logs.getTotalElements()).isEqualTo(1);
        assertThat(logs.getContent().get(0).getActionType()).isEqualTo("MANUAL_STATUS_CORRECTION");
    }

    @Test
    void auditLog_filterByActingAdmin_returnsCorrectEntries() {
        User otherAdmin = factory.createAdmin();

        auditLogService.log("BALANCE_CREDIT", "customer", customer.getId().toString(),
                null, Map.of("amount", 1000), admin.getId());
        auditLogService.log("PRODUCT_DISABLE", "product", product.getId().toString(),
                null, Map.of("isAvailable", false), otherAdmin.getId());

        Page<AdminAuditLog> adminLogs = auditLogRepository
                .findByActingAdminOrderByCreatedAtDesc(admin.getId(), PageRequest.of(0, 10));
        Page<AdminAuditLog> otherLogs = auditLogRepository
                .findByActingAdminOrderByCreatedAtDesc(otherAdmin.getId(), PageRequest.of(0, 10));

        assertThat(adminLogs.getTotalElements()).isEqualTo(1);
        assertThat(otherLogs.getTotalElements()).isEqualTo(1);
        assertThat(adminLogs.getContent().get(0).getActionType()).isEqualTo("BALANCE_CREDIT");
        assertThat(otherLogs.getContent().get(0).getActionType()).isEqualTo("PRODUCT_DISABLE");
    }

    @Test
    void auditLog_nullOldValue_isAllowed() {
        // Creation events have null old_value (BR-AUD-02)
        auditLogService.log("PRODUCT_CREATE", "product", product.getId().toString(),
                null, Map.of("name", product.getName()), admin.getId());

        AdminAuditLog entry = auditLogRepository
                .findByTargetEntityOrderByCreatedAtDesc("product", PageRequest.of(0, 1))
                .getContent().get(0);

        assertThat(entry.getOldValue()).isNull();
        assertThat(entry.getNewValue()).contains(product.getName());
    }
}
