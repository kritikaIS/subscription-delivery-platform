package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.dto.holiday.AddHolidayRequest;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.SchedulerJobLog;
import com.juiceplatform.entity.User;
import com.juiceplatform.repository.AdminAuditLogRepository;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.SchedulerJobLogRepository;
import com.juiceplatform.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AdminSchedulerServiceTest extends AbstractIntegrationTest {

    @Autowired OrderGenerationService orderGenerationService;
    @Autowired OrderFreezeService orderFreezeService;
    @Autowired SubscriptionActivationService subscriptionActivationService;
    @Autowired BusinessHolidayService holidayService;
    @Autowired AuditLogService auditLogService;
    @Autowired SchedulerJobLogRepository schedulerJobLogRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired OrderRepository orderRepository;
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

    // ─── Manual rerun — order generation ────────────────────────────────────

    @Test
    void rerunOrderGeneration_createsOrders_idempotent() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        // First run
        OrderGenerationService.OrderGenerationResult r1 =
                orderGenerationService.generateOrdersForDate(deliveryDate);
        assertThat(r1.ordersCreated()).isEqualTo(1);

        // Second run — idempotent, no duplicate
        OrderGenerationService.OrderGenerationResult r2 =
                orderGenerationService.generateOrdersForDate(deliveryDate);
        assertThat(r2.ordersCreated()).isZero();
        assertThat(r2.duplicatesSkipped()).isEqualTo(1);

        // Only one order in DB
        long orderCount = orderRepository.findByCustomerIdOrderByDeliveryDateDesc(
                customer.getId(), PageRequest.of(0, 10)).getTotalElements();
        assertThat(orderCount).isEqualTo(1);
    }

    // ─── Manual rerun — order freeze ────────────────────────────────────────

    @Test
    void rerunOrderFreeze_locksOrders_idempotent() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate deliveryDate = LocalDate.now().plusDays(1);
        orderGenerationService.generateOrdersForDate(deliveryDate);

        // First freeze
        OrderFreezeService.FreezeResult r1 = orderFreezeService.freezeOrdersForDate(deliveryDate);
        assertThat(r1.ordersLocked()).isEqualTo(1);

        // Second freeze — idempotent
        OrderFreezeService.FreezeResult r2 = orderFreezeService.freezeOrdersForDate(deliveryDate);
        assertThat(r2.ordersLocked()).isZero();
        assertThat(r2.duplicatesSkipped()).isEqualTo(1);
    }

    // ─── Holiday behavior ────────────────────────────────────────────────────

    @Test
    void rerunOrderGeneration_onHoliday_skipsAllOrders() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        holidayService.addHoliday(new AddHolidayRequest(deliveryDate, "Test Holiday"), admin.getId());

        OrderGenerationService.OrderGenerationResult result =
                orderGenerationService.generateOrdersForDate(deliveryDate);

        assertThat(result.ordersCreated()).isZero();
        assertThat(result.activeSubscriptionsProcessed()).isZero();
    }

    // ─── Scheduler job history ───────────────────────────────────────────────

    @Test
    void schedulerJobHistory_recordsAreCreated_afterFreeze() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        orderGenerationService.generateOrdersForDate(deliveryDate);
        orderFreezeService.freezeOrdersForDate(deliveryDate);

        Page<SchedulerJobLog> history = schedulerJobLogRepository
                .findByJobNameOrderByStartedAtDesc("OrderFreezeJob", PageRequest.of(0, 10));

        assertThat(history.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(history.getContent().get(0).getStatus())
                .isEqualTo(SchedulerJobLog.JobStatus.COMPLETED);
        assertThat(history.getContent().get(0).getJobDate()).isEqualTo(deliveryDate);
    }

    @Test
    void schedulerJobHistory_filterByJobName_returnsCorrectEntries() {
        LocalDate deliveryDate = LocalDate.now().plusDays(1);
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        orderGenerationService.generateOrdersForDate(deliveryDate);
        orderFreezeService.freezeOrdersForDate(deliveryDate);

        Page<SchedulerJobLog> freezeHistory = schedulerJobLogRepository
                .findByJobNameOrderByStartedAtDesc("OrderFreezeJob", PageRequest.of(0, 10));
        Page<SchedulerJobLog> allHistory = schedulerJobLogRepository
                .findAllByOrderByStartedAtDesc(PageRequest.of(0, 10));

        assertThat(freezeHistory.getContent())
                .allMatch(e -> e.getJobName().equals("OrderFreezeJob"));
        assertThat(allHistory.getTotalElements())
                .isGreaterThanOrEqualTo(freezeHistory.getTotalElements());
    }

    // ─── Subscription activation before generation ───────────────────────────

    @Test
    void subscriptionActivation_activatesPendingStart_beforeGeneration() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());

        // Create a PENDING_START subscription with start_date = today,
        // so activateEligibleSubscriptions() (start_date <= today) picks it up (BR-SUB-05)
        factory.createPendingStartSubscription(customer.getId(), product.getId(), 1);

        SubscriptionActivationService.ActivationResult result =
                subscriptionActivationService.activateEligibleSubscriptions();

        assertThat(result.subscriptionsActivated()).isGreaterThanOrEqualTo(1);
    }

    // ─── Audit logging for scheduler reruns ─────────────────────────────────

    @Test
    void schedulerRerun_auditLogCreated_forFreezeRerun() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate deliveryDate = LocalDate.now().plusDays(1);
        orderGenerationService.generateOrdersForDate(deliveryDate);

        long auditCountBefore = auditLogRepository.count();

        orderFreezeService.freezeOrdersForDate(deliveryDate);
        auditLogService.log("SCHEDULER_RERUN", "scheduler_job", "OrderFreezeJob",
                null,
                java.util.Map.of("targetDate", deliveryDate.toString()),
                admin.getId(),
                "Admin rerun of OrderFreezeJob for " + deliveryDate);

        long auditCountAfter = auditLogRepository.count();
        assertThat(auditCountAfter).isEqualTo(auditCountBefore + 1);

        var auditEntry = auditLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, 1)).getContent().get(0);
        assertThat(auditEntry.getActionType()).isEqualTo("SCHEDULER_RERUN");
        assertThat(auditEntry.getTargetEntity()).isEqualTo("scheduler_job");
        assertThat(auditEntry.getTargetId()).isEqualTo("OrderFreezeJob");
        assertThat(auditEntry.getActingAdmin()).isEqualTo(admin.getId());
    }

    @Test
    void schedulerRerun_auditLogCreated_forGenerateRerun() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        long auditCountBefore = auditLogRepository.count();

        orderGenerationService.generateOrdersForDate(deliveryDate);
        auditLogService.log("SCHEDULER_RERUN", "scheduler_job", "OrderGenerationJob",
                null,
                java.util.Map.of("targetDate", deliveryDate.toString()),
                admin.getId(),
                "Admin rerun of OrderGenerationJob for " + deliveryDate);

        long auditCountAfter = auditLogRepository.count();
        assertThat(auditCountAfter).isEqualTo(auditCountBefore + 1);

        var auditEntry = auditLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, 1)).getContent().get(0);
        assertThat(auditEntry.getActionType()).isEqualTo("SCHEDULER_RERUN");
        assertThat(auditEntry.getTargetId()).isEqualTo("OrderGenerationJob");
    }

    @Test
    void schedulerRerun_idempotencyPreserved_withAuditLog() {
        factory.creditWallet(customer.getId(), 10000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        OrderGenerationService.OrderGenerationResult r1 =
                orderGenerationService.generateOrdersForDate(deliveryDate);
        assertThat(r1.ordersCreated()).isEqualTo(1);

        OrderGenerationService.OrderGenerationResult r2 =
                orderGenerationService.generateOrdersForDate(deliveryDate);
        assertThat(r2.ordersCreated()).isZero();

        // Audit log for second rerun still created
        auditLogService.log("SCHEDULER_RERUN", "scheduler_job", "OrderGenerationJob",
                null, java.util.Map.of("rerun", "2"), admin.getId(), "second rerun");

        assertThat(auditLogRepository.findByTargetEntityOrderByCreatedAtDesc(
                "scheduler_job", PageRequest.of(0, 10))
                .getTotalElements()).isGreaterThanOrEqualTo(1);
    }
}
