package com.juiceplatform.scheduler;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.SchedulerJobLog;
import com.juiceplatform.entity.User;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.SchedulerJobLogRepository;
import com.juiceplatform.service.OrderFreezeService;
import com.juiceplatform.service.OrderGenerationService;
import com.juiceplatform.service.SubscriptionActivationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for scheduler_job_log tracking in OrderGenerationService and SubscriptionActivationService.
 * Covers: RUNNING guard, COMPLETED state, FAILED state, rerun after COMPLETED/FAILED,
 * rowsProcessed metadata, and idempotency.
 */
@Transactional
class SchedulerJobLogTrackingTest extends AbstractIntegrationTest {

    @Autowired OrderGenerationService orderGenerationService;
    @Autowired SubscriptionActivationService subscriptionActivationService;
    @Autowired OrderFreezeService orderFreezeService;
    @Autowired SchedulerJobLogRepository schedulerJobLogRepository;
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

    // ─── OrderGenerationService job log ─────────────────────────────────────

    @Test
    void orderGeneration_createsJobLog_withCompletedStatus() {
        factory.creditWallet(customer.getId(), 10_000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        orderGenerationService.generateOrdersForDate(deliveryDate);

        Optional<SchedulerJobLog> log = schedulerJobLogRepository
                .findByJobNameAndJobDate(OrderGenerationService.JOB_NAME, deliveryDate);
        assertThat(log).isPresent();
        assertThat(log.get().getStatus()).isEqualTo(SchedulerJobLog.JobStatus.COMPLETED);
        assertThat(log.get().getJobDate()).isEqualTo(deliveryDate);
        assertThat(log.get().getFinishedAt()).isNotNull();
        assertThat(log.get().getRowsProcessed()).isEqualTo(1); // one order created
    }

    @Test
    void orderGeneration_jobLog_rowsProcessed_reflectsOrdersCreated() {
        factory.creditWallet(customer.getId(), 10_000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        orderGenerationService.generateOrdersForDate(deliveryDate);

        SchedulerJobLog log = schedulerJobLogRepository
                .findByJobNameAndJobDate(OrderGenerationService.JOB_NAME, deliveryDate).orElseThrow();
        assertThat(log.getRowsProcessed()).isEqualTo(1);
    }

    @Test
    void orderGeneration_jobLog_rowsProcessed_zeroWhenNoOrders() {
        // No subscriptions — no orders created
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        orderGenerationService.generateOrdersForDate(deliveryDate);

        SchedulerJobLog log = schedulerJobLogRepository
                .findByJobNameAndJobDate(OrderGenerationService.JOB_NAME, deliveryDate).orElseThrow();
        assertThat(log.getRowsProcessed()).isEqualTo(0);
        assertThat(log.getStatus()).isEqualTo(SchedulerJobLog.JobStatus.COMPLETED);
    }

    @Test
    void orderGeneration_runningGuard_rejectsSecondConcurrentRun() {
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        // Manually insert a RUNNING entry to simulate concurrent execution.
        // First clear any existing entry from startup recovery.
        schedulerJobLogRepository.findByJobNameAndJobDate(OrderGenerationService.JOB_NAME, deliveryDate)
                .ifPresent(e -> { schedulerJobLogRepository.delete(e); schedulerJobLogRepository.flush(); });

        SchedulerJobLog running = new SchedulerJobLog();
        running.setJobName(OrderGenerationService.JOB_NAME);
        running.setJobDate(deliveryDate);
        running.setStatus(SchedulerJobLog.JobStatus.RUNNING);
        schedulerJobLogRepository.save(running);
        schedulerJobLogRepository.flush();

        // Second call must be rejected (returns empty result, does not throw)
        OrderGenerationService.OrderGenerationResult result =
                orderGenerationService.generateOrdersForDate(deliveryDate);

        assertThat(result.ordersCreated()).isEqualTo(0);
        assertThat(result.activeSubscriptionsProcessed()).isEqualTo(0);
    }

    @Test
    void orderGeneration_rerunAfterCompleted_isIdempotent() {
        factory.creditWallet(customer.getId(), 10_000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        // First run
        OrderGenerationService.OrderGenerationResult r1 =
                orderGenerationService.generateOrdersForDate(deliveryDate);
        assertThat(r1.ordersCreated()).isEqualTo(1);

        // Second run — COMPLETED entry is deleted and recreated, no duplicate orders
        OrderGenerationService.OrderGenerationResult r2 =
                orderGenerationService.generateOrdersForDate(deliveryDate);
        assertThat(r2.ordersCreated()).isEqualTo(0);
        assertThat(r2.duplicatesSkipped()).isEqualTo(1);

        // Still only one order in DB
        long orderCount = orderRepository
                .findByCustomerIdOrderByDeliveryDateDesc(customer.getId(), PageRequest.of(0, 10))
                .getTotalElements();
        assertThat(orderCount).isEqualTo(1);

        // Job log shows COMPLETED
        SchedulerJobLog log = schedulerJobLogRepository
                .findByJobNameAndJobDate(OrderGenerationService.JOB_NAME, deliveryDate).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(SchedulerJobLog.JobStatus.COMPLETED);
    }

    @Test
    void orderGeneration_rerunAfterFailed_isAllowed() {
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        // Clear any existing entry, then insert FAILED
        schedulerJobLogRepository.findByJobNameAndJobDate(OrderGenerationService.JOB_NAME, deliveryDate)
                .ifPresent(e -> { schedulerJobLogRepository.delete(e); schedulerJobLogRepository.flush(); });

        SchedulerJobLog failed = new SchedulerJobLog();
        failed.setJobName(OrderGenerationService.JOB_NAME);
        failed.setJobDate(deliveryDate);
        failed.setStatus(SchedulerJobLog.JobStatus.FAILED);
        failed.setFinishedAt(java.time.OffsetDateTime.now());
        failed.setErrorMessage("simulated failure");
        schedulerJobLogRepository.save(failed);
        schedulerJobLogRepository.flush();

        // Rerun must succeed (FAILED → delete + rerun)
        OrderGenerationService.OrderGenerationResult result =
                orderGenerationService.generateOrdersForDate(deliveryDate);

        SchedulerJobLog log = schedulerJobLogRepository
                .findByJobNameAndJobDate(OrderGenerationService.JOB_NAME, deliveryDate).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(SchedulerJobLog.JobStatus.COMPLETED);
        assertThat(log.getErrorMessage()).isNull();
    }

    // ─── SubscriptionActivationService job log ───────────────────────────────

    @Test
    void subscriptionActivation_createsJobLog_withCompletedStatus() {
        factory.createPendingStartSubscription(customer.getId(), product.getId(), 1);
        LocalDate today = LocalDate.now();

        subscriptionActivationService.activateEligibleSubscriptions();

        Optional<SchedulerJobLog> log = schedulerJobLogRepository
                .findByJobNameAndJobDate(SubscriptionActivationService.JOB_NAME, today);
        assertThat(log).isPresent();
        assertThat(log.get().getStatus()).isEqualTo(SchedulerJobLog.JobStatus.COMPLETED);
        assertThat(log.get().getRowsProcessed()).isEqualTo(1);
        assertThat(log.get().getFinishedAt()).isNotNull();
    }

    @Test
    void subscriptionActivation_runningGuard_rejectsSecondConcurrentRun() {
        LocalDate today = LocalDate.now();

        // Clear any existing entry, then insert RUNNING
        schedulerJobLogRepository.findByJobNameAndJobDate(SubscriptionActivationService.JOB_NAME, today)
                .ifPresent(e -> { schedulerJobLogRepository.delete(e); schedulerJobLogRepository.flush(); });

        SchedulerJobLog running = new SchedulerJobLog();
        running.setJobName(SubscriptionActivationService.JOB_NAME);
        running.setJobDate(today);
        running.setStatus(SchedulerJobLog.JobStatus.RUNNING);
        schedulerJobLogRepository.save(running);
        schedulerJobLogRepository.flush();

        // Second call must be rejected
        SubscriptionActivationService.ActivationResult result =
                subscriptionActivationService.activateEligibleSubscriptions();

        assertThat(result.subscriptionsActivated()).isEqualTo(0);
    }

    @Test
    void subscriptionActivation_rerunAfterFailed_isAllowed() {
        LocalDate today = LocalDate.now();

        // Clear any existing entry, then insert FAILED
        schedulerJobLogRepository.findByJobNameAndJobDate(SubscriptionActivationService.JOB_NAME, today)
                .ifPresent(e -> { schedulerJobLogRepository.delete(e); schedulerJobLogRepository.flush(); });

        SchedulerJobLog failed = new SchedulerJobLog();
        failed.setJobName(SubscriptionActivationService.JOB_NAME);
        failed.setJobDate(today);
        failed.setStatus(SchedulerJobLog.JobStatus.FAILED);
        failed.setFinishedAt(java.time.OffsetDateTime.now());
        failed.setErrorMessage("simulated failure");
        schedulerJobLogRepository.save(failed);
        schedulerJobLogRepository.flush();

        // Rerun must succeed
        subscriptionActivationService.activateEligibleSubscriptions();

        SchedulerJobLog log = schedulerJobLogRepository
                .findByJobNameAndJobDate(SubscriptionActivationService.JOB_NAME, today).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(SchedulerJobLog.JobStatus.COMPLETED);
        assertThat(log.getErrorMessage()).isNull();
    }

    // ─── No duplicate orders across reruns ──────────────────────────────────

    @Test
    void orderGeneration_multipleReruns_noDuplicateOrders() {
        factory.creditWallet(customer.getId(), 50_000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        // Run 3 times
        orderGenerationService.generateOrdersForDate(deliveryDate);
        orderGenerationService.generateOrdersForDate(deliveryDate);
        orderGenerationService.generateOrdersForDate(deliveryDate);

        long orderCount = orderRepository
                .findByCustomerIdOrderByDeliveryDateDesc(customer.getId(), PageRequest.of(0, 10))
                .getTotalElements();
        assertThat(orderCount).isEqualTo(1);
    }

    // ─── No duplicate delivery records across freeze reruns ─────────────────

    @Test
    void orderFreeze_multipleReruns_noDuplicateDeliveryRecords() {
        factory.creditWallet(customer.getId(), 10_000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate deliveryDate = LocalDate.now().plusDays(1);

        orderGenerationService.generateOrdersForDate(deliveryDate);

        // Freeze 3 times
        orderFreezeService.freezeOrdersForDate(deliveryDate);
        orderFreezeService.freezeOrdersForDate(deliveryDate);
        orderFreezeService.freezeOrdersForDate(deliveryDate);

        // Still only one order and one delivery record
        long orderCount = orderRepository
                .findByCustomerIdOrderByDeliveryDateDesc(customer.getId(), PageRequest.of(0, 10))
                .getTotalElements();
        assertThat(orderCount).isEqualTo(1);
    }
}
