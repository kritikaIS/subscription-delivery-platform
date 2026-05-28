package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.dto.delivery.OrderCorrectionRequest;
import com.juiceplatform.dto.delivery.OrderCorrectionResponse;
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
class OrderCorrectionServiceTest extends AbstractIntegrationTest {

    @Autowired OrderCorrectionService correctionService;
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
        product = factory.createProduct(3000L);
        admin = factory.createAdmin();
    }

    // --- DELIVERED → SKIPPED ---

    @Test
    void deliveredToSkipped_withSystemError_issuesRefund() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                3000L, 1, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        deliveryService.markDelivered(order.getId(), admin.getId());
        long balanceAfterDelivery = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .map(WalletLedger::getRunningBalancePaise).orElse(0L);
        assertThat(balanceAfterDelivery).isEqualTo(7000L);

        OrderCorrectionRequest request = new OrderCorrectionRequest(
                "SKIPPED", "DAMAGED", true, null, null);
        OrderCorrectionResponse response = correctionService.correctOrder(order.getId(), request, admin.getId());

        assertThat(response.isAutoRefundIssued()).isTrue();
        assertThat(response.getStatus()).isEqualTo("SKIPPED");

        long balanceAfterRefund = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .map(WalletLedger::getRunningBalancePaise).orElse(0L);
        assertThat(balanceAfterRefund).isEqualTo(10000L);

        WalletLedger refund = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElseThrow();
        assertThat(refund.getEntryType()).isEqualTo(WalletLedger.EntryType.REFUND);
        assertThat(refund.getSourceType()).isEqualTo(WalletLedger.SourceType.HISTORICAL_CORRECTION);

        DeliveryRecord record = deliveryRecordRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(DeliveryRecord.DeliveryRecordStatus.SKIPPED);
        assertThat(record.getDeliveredAt()).isNull();
    }

    @Test
    void deliveredToSkipped_withoutSystemError_noRefund() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                3000L, 1, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        deliveryService.markDelivered(order.getId(), admin.getId());

        OrderCorrectionRequest request = new OrderCorrectionRequest(
                "SKIPPED", "OTHER", false, null, null);
        OrderCorrectionResponse response = correctionService.correctOrder(order.getId(), request, admin.getId());

        assertThat(response.isAutoRefundIssued()).isFalse();

        long balance = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .map(WalletLedger::getRunningBalancePaise).orElse(0L);
        assertThat(balance).isEqualTo(7000L);
    }

    // --- SKIPPED → DELIVERED ---

    @Test
    void skippedToDelivered_insertsDebitAndUpdatesStatus() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                3000L, 1, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        deliveryService.markSkipped(order.getId(), "CUSTOMER_UNAVAILABLE", admin.getId());

        OrderCorrectionRequest request = new OrderCorrectionRequest(
                "DELIVERED", null, null, null, null);
        OrderCorrectionResponse response = correctionService.correctOrder(order.getId(), request, admin.getId());

        assertThat(response.getStatus()).isEqualTo("DELIVERED");

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(Order.OrderStatus.DELIVERED);
        assertThat(updatedOrder.getSkipReason()).isNull();

        DeliveryRecord record = deliveryRecordRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(DeliveryRecord.DeliveryRecordStatus.DELIVERED);
        assertThat(record.getDeliveredAt()).isNotNull();

        WalletLedger debit = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElseThrow();
        assertThat(debit.getEntryType()).isEqualTo(WalletLedger.EntryType.DEBIT);
        assertThat(debit.getSourceType()).isEqualTo(WalletLedger.SourceType.HISTORICAL_CORRECTION_DEBIT);
        assertThat(debit.getRunningBalancePaise()).isEqualTo(7000L);
    }

    @Test
    void skippedToDelivered_negativeBalancePermitted() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                3000L, 1, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());
        deliveryService.markSkipped(order.getId(), "DAMAGED", admin.getId());

        OrderCorrectionRequest request = new OrderCorrectionRequest(
                "DELIVERED", null, null, null, null);
        OrderCorrectionResponse response = correctionService.correctOrder(order.getId(), request, admin.getId());

        assertThat(response.getStatus()).isEqualTo("DELIVERED");

        long balance = walletLedgerRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .map(WalletLedger::getRunningBalancePaise).orElse(0L);
        assertThat(balance).isEqualTo(-3000L);
    }

    // --- LOCKED → CANCELLED ---

    @Test
    void lockedToCancelled_retainsDeliveryRecord() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                3000L, 1, LocalDate.now().plusDays(1));
        factory.createPendingDeliveryRecord(order.getId(), order.getDeliveryDate());

        OrderCorrectionRequest request = new OrderCorrectionRequest(
                "CANCELLED", null, null, null, "Operational cancellation");
        correctionService.correctOrder(order.getId(), request, admin.getId());

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
        assertThat(updatedOrder.getCancellationComment()).isEqualTo("Operational cancellation");
        assertThat(updatedOrder.getCancellationCommentedBy()).isEqualTo(admin.getId());

        DeliveryRecord record = deliveryRecordRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(DeliveryRecord.DeliveryRecordStatus.CANCELLED);
    }

    // --- Invalid transitions ---

    @Test
    void invalidTransition_scheduledToDelivered_throws409() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                3000L, 1, LocalDate.now().plusDays(1));
        order.setStatus(Order.OrderStatus.SCHEDULED);
        orderRepository.save(order);

        OrderCorrectionRequest request = new OrderCorrectionRequest(
                "DELIVERED", null, null, null, null);

        assertThatThrownBy(() -> correctionService.correctOrder(order.getId(), request, admin.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void invalidTransition_cancelledToActive_throws409() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                3000L, 1, LocalDate.now().plusDays(1));
        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);

        OrderCorrectionRequest request = new OrderCorrectionRequest(
                "DELIVERED", null, null, null, null);

        assertThatThrownBy(() -> correctionService.correctOrder(order.getId(), request, admin.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_STATUS_TRANSITION");
    }
}
