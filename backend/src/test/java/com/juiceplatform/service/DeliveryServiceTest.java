package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.dto.delivery.MarkDeliveredResponse;
import com.juiceplatform.dto.delivery.MarkSkippedResponse;
import com.juiceplatform.entity.DeliveryRecord;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.User;
import com.juiceplatform.entity.WalletLedger;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.DeliveryRecordRepository;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.WalletLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class DeliveryServiceTest extends AbstractIntegrationTest {

    @Autowired DeliveryService deliveryService;
    @Autowired TestDataFactory factory;
    @Autowired OrderRepository orderRepository;
    @Autowired DeliveryRecordRepository deliveryRecordRepository;
    @Autowired WalletLedgerRepository walletLedgerRepository;

    User customer;
    Product product;
    User admin;

    @BeforeEach
    void setUp() {
        customer = factory.createCustomer();
        factory.createAddress(customer.getId());
        product = factory.createProduct(2500L);
        admin = factory.createAdmin();
    }

    // --- Mark Delivered ---

    @Test
    void markDelivered_happyPath_updatesOrderAndDeductsWallet() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 2);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 2, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        MarkDeliveredResponse response = deliveryService.markDelivered(order.getId(), admin.getId());

        assertThat(response.getStatus()).isEqualTo("DELIVERED");
        assertThat(response.getAmountDeductedPaise()).isEqualTo(5000L);
        assertThat(response.getNewWalletBalancePaise()).isEqualTo(5000L);

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(Order.OrderStatus.DELIVERED);

        DeliveryRecord record = deliveryRecordRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(DeliveryRecord.DeliveryRecordStatus.DELIVERED);
        assertThat(record.getDeliveredAt()).isNotNull();

        WalletLedger ledger = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElseThrow();
        assertThat(ledger.getEntryType()).isEqualTo(WalletLedger.EntryType.DEBIT);
        assertThat(ledger.getSourceType()).isEqualTo(WalletLedger.SourceType.DELIVERY_DEBIT);
        assertThat(ledger.getAmountPaise()).isEqualTo(5000L);
        assertThat(ledger.getRunningBalancePaise()).isEqualTo(5000L);
        assertThat(ledger.getOrderId()).isEqualTo(order.getId());
    }

    @Test
    void markDelivered_idempotent_doesNotCreateSecondDebit() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 1, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        deliveryService.markDelivered(order.getId(), admin.getId());
        deliveryService.markDelivered(order.getId(), admin.getId()); // second call — idempotent

        long debitCount = walletLedgerRepository
                .findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId(),
                        org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .filter(e -> e.getSourceType() == WalletLedger.SourceType.DELIVERY_DEBIT)
                .count();
        assertThat(debitCount).isEqualTo(1);
    }

    @Test
    void markDelivered_insufficientBalance_throws400() {
        // No wallet credit — balance = 0
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 2);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 2, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        assertThatThrownBy(() -> deliveryService.markDelivered(order.getId(), admin.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("insufficient")
                .extracting("code").isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    void markDelivered_notLockedOrder_throws409() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 1, LocalDate.now().plusDays(1));
        order.setStatus(Order.OrderStatus.SCHEDULED);
        orderRepository.save(order);

        assertThatThrownBy(() -> deliveryService.markDelivered(order.getId(), admin.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ORDER_NOT_DELIVERABLE");
    }

    // --- Mark Skipped ---

    @Test
    void markSkipped_happyPath_updatesOrderAndRecord_noWalletDebit() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 1, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        MarkSkippedResponse response = deliveryService.markSkipped(
                order.getId(), "CUSTOMER_UNAVAILABLE", admin.getId());

        assertThat(response.getStatus()).isEqualTo("SKIPPED");
        assertThat(response.getSkipReason()).isEqualTo("CUSTOMER_UNAVAILABLE");

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(Order.OrderStatus.SKIPPED);
        assertThat(updatedOrder.getSkipReason()).isEqualTo(Order.SkipReason.CUSTOMER_UNAVAILABLE);

        DeliveryRecord record = deliveryRecordRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(DeliveryRecord.DeliveryRecordStatus.SKIPPED);

        // No wallet debit
        long debitCount = walletLedgerRepository
                .findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId(),
                        org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .filter(e -> e.getEntryType() == WalletLedger.EntryType.DEBIT)
                .count();
        assertThat(debitCount).isZero();
    }

    @Test
    void markSkipped_invalidSkipReason_throws400() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 1, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        assertThatThrownBy(() -> deliveryService.markSkipped(order.getId(), "INVALID_REASON", admin.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_SKIP_REASON");
    }

    @Test
    void markSkipped_notLockedOrder_throws409() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 1, LocalDate.now().plusDays(1));
        order.setStatus(Order.OrderStatus.SCHEDULED);
        orderRepository.save(order);

        assertThatThrownBy(() -> deliveryService.markSkipped(order.getId(), "DAMAGED", admin.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ORDER_NOT_SKIPPABLE");
    }
}
