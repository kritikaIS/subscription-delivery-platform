package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.dto.deliverysheet.DeliverySheetResponse;
import com.juiceplatform.entity.DeliverySheetSnapshot;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.DeliverySheetSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class DeliverySheetServiceTest extends AbstractIntegrationTest {

    @Autowired DeliverySheetService deliverySheetService;
    @Autowired BusinessHolidayService holidayService;
    @Autowired DeliverySheetSnapshotRepository snapshotRepository;
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

    // ─── Snapshot generation ─────────────────────────────────────────────────

    @Test
    void generateSnapshot_withLockedOrders_createsSnapshot() {
        LocalDate deliveryDate = LocalDate.now().plusDays(1);
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 2);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 2, deliveryDate);
        factory.createPendingDeliveryRecord(order.getId(), deliveryDate);

        DeliverySheetResponse response = deliverySheetService.generateSnapshot(
                deliveryDate, DeliverySheetSnapshot.GeneratedBySource.SCHEDULER, null);

        assertThat(response.getDeliveryDate()).isEqualTo(deliveryDate);
        assertThat(response.getOrders()).hasSize(1);
        assertThat(response.getOrders().get(0).getQuantity()).isEqualTo(2);
        assertThat(response.getOrders().get(0).getProductName()).isEqualTo(product.getName());
        assertThat(response.getJuiceSummary()).hasSize(1);
        assertThat(response.getJuiceSummary().get(0).getTotalQuantity()).isEqualTo(2);

        // Snapshot persisted
        assertThat(snapshotRepository.existsByDeliveryDate(deliveryDate)).isTrue();
    }

    @Test
    void generateSnapshot_noOrders_createsEmptySnapshot() {
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        DeliverySheetResponse response = deliverySheetService.generateSnapshot(
                deliveryDate, DeliverySheetSnapshot.GeneratedBySource.SCHEDULER, null);

        assertThat(response.getOrders()).isEmpty();
        assertThat(response.getJuiceSummary()).isEmpty();
        assertThat(snapshotRepository.existsByDeliveryDate(deliveryDate)).isTrue();
    }

    // ─── Idempotency / rerun ─────────────────────────────────────────────────

    @Test
    void generateSnapshot_rerun_replacesExistingSnapshot() {
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        // First run — no orders
        deliverySheetService.generateSnapshot(deliveryDate,
                DeliverySheetSnapshot.GeneratedBySource.SCHEDULER, null);
        assertThat(snapshotRepository.findByDeliveryDate(deliveryDate)).isPresent();

        // Add an order and rerun
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 1, deliveryDate);
        factory.createPendingDeliveryRecord(order.getId(), deliveryDate);

        DeliverySheetResponse rerun = deliverySheetService.generateSnapshot(deliveryDate,
                DeliverySheetSnapshot.GeneratedBySource.ADMIN_RERUN, admin.getId());

        // Still only one snapshot row
        assertThat(snapshotRepository.findAll().stream()
                .filter(s -> s.getDeliveryDate().equals(deliveryDate)).count()).isEqualTo(1);

        // Rerun has the new order
        assertThat(rerun.getOrders()).hasSize(1);
    }

    // ─── Juice summary aggregation ───────────────────────────────────────────

    @Test
    void generateSnapshot_multipleOrdersSameProduct_aggregatesJuiceSummary() {
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        // Two customers, same product
        User customer2 = factory.createCustomer();
        factory.createAddress(customer2.getId());

        var sub1 = factory.createActiveSubscription(customer.getId(), product.getId(), 2);
        var order1 = factory.createLockedOrder(customer.getId(), sub1.getId(), product.getId(),
                2500L, 2, deliveryDate);
        factory.createPendingDeliveryRecord(order1.getId(), deliveryDate);

        var sub2 = factory.createActiveSubscription(customer2.getId(), product.getId(), 3);
        var order2 = factory.createLockedOrder(customer2.getId(), sub2.getId(), product.getId(),
                2500L, 3, deliveryDate);
        factory.createPendingDeliveryRecord(order2.getId(), deliveryDate);

        DeliverySheetResponse response = deliverySheetService.generateSnapshot(
                deliveryDate, DeliverySheetSnapshot.GeneratedBySource.SCHEDULER, null);

        assertThat(response.getOrders()).hasSize(2);
        assertThat(response.getJuiceSummary()).hasSize(1);
        assertThat(response.getJuiceSummary().get(0).getTotalQuantity()).isEqualTo(5); // 2 + 3
    }

    // ─── CANCELLED delivery records excluded ─────────────────────────────────

    @Test
    void generateSnapshot_cancelledDeliveryRecord_excluded() {
        LocalDate deliveryDate = LocalDate.now().plusDays(1);
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        var order = factory.createLockedOrder(customer.getId(), sub.getId(), product.getId(),
                2500L, 1, deliveryDate);

        // Create a CANCELLED delivery record
        var record = factory.createPendingDeliveryRecord(order.getId(), deliveryDate);
        record.setStatus(com.juiceplatform.entity.DeliveryRecord.DeliveryRecordStatus.CANCELLED);

        DeliverySheetResponse response = deliverySheetService.generateSnapshot(
                deliveryDate, DeliverySheetSnapshot.GeneratedBySource.SCHEDULER, null);

        // CANCELLED delivery record excluded from sheet
        assertThat(response.getOrders()).isEmpty();
        assertThat(response.getJuiceSummary()).isEmpty();
    }

    // ─── Get snapshot ────────────────────────────────────────────────────────

    @Test
    void getSnapshot_existingDate_returnsSnapshot() {
        LocalDate deliveryDate = LocalDate.now().plusDays(1);
        deliverySheetService.generateSnapshot(deliveryDate,
                DeliverySheetSnapshot.GeneratedBySource.SCHEDULER, null);

        DeliverySheetResponse response = deliverySheetService.getSnapshot(deliveryDate);
        assertThat(response.getDeliveryDate()).isEqualTo(deliveryDate);
    }

    @Test
    void getSnapshot_nonExistentDate_throws404() {
        assertThatThrownBy(() -> deliverySheetService.getSnapshot(LocalDate.now().plusDays(99)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("RESOURCE_NOT_FOUND");
    }
}
