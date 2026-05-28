package com.juiceplatform.scheduler;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.SchedulerJobLog;
import com.juiceplatform.entity.User;
import com.juiceplatform.repository.DeliveryRecordRepository;
import com.juiceplatform.repository.DeliverySheetSnapshotRepository;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.SchedulerJobLogRepository;
import com.juiceplatform.service.OrderFreezeService;
import com.juiceplatform.service.OrderGenerationService;
import com.juiceplatform.service.SubscriptionActivationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SchedulerStartupRecovery (BR-SCH-04).
 *
 * IMPORTANT: SchedulerStartupRecovery is an ApplicationRunner and runs at application startup.
 * By the time these tests execute, the previous 3 days have already been processed.
 * Tests that need to simulate "missed" jobs must first clear the relevant job log entries.
 *
 * Tests verify:
 * - Missed jobs are recovered (after clearing log entries)
 * - COMPLETED jobs are skipped
 * - RUNNING jobs are skipped (concurrent guard)
 * - FAILED jobs are rerun
 * - No duplicate orders, delivery_records, or delivery sheets
 * - Recovery is idempotent
 */
@Transactional
class SchedulerStartupRecoveryTest extends AbstractIntegrationTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired SchedulerStartupRecovery recovery;
    @Autowired SchedulerJobLogRepository schedulerJobLogRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired DeliveryRecordRepository deliveryRecordRepository;
    @Autowired DeliverySheetSnapshotRepository snapshotRepository;
    @Autowired TestDataFactory factory;

    User admin;
    User customer;
    Product product;

    // The "missed" delivery date used in most tests: yesterday's operational date → today's delivery
    LocalDate missedDeliveryDate;

    @BeforeEach
    void setUp() {
        admin = factory.createAdmin();
        customer = factory.createCustomer();
        factory.createAddress(customer.getId());
        product = factory.createProduct(2500L);
        missedDeliveryDate = LocalDate.now(IST).minusDays(1).plusDays(1); // = today
    }

    // ─── Missed job detection ────────────────────────────────────────────────

    @Test
    void recovery_completedJobsAreSkipped() throws Exception {
        // Ensure OrderGenerationJob is COMPLETED for the missed delivery date
        ensureCompleted(OrderGenerationService.JOB_NAME, missedDeliveryDate);

        long orderCountBefore = orderRepository.count();
        recovery.run(new DefaultApplicationArguments());
        long orderCountAfter = orderRepository.count();

        // No new orders — generation was already COMPLETED
        assertThat(orderCountAfter).isEqualTo(orderCountBefore);
    }

    @Test
    void recovery_runningJobsAreSkipped() throws Exception {
        // Clear any existing entry and insert RUNNING
        clearJobLog(OrderGenerationService.JOB_NAME, missedDeliveryDate);
        markRunning(OrderGenerationService.JOB_NAME, missedDeliveryDate);

        recovery.run(new DefaultApplicationArguments());

        // RUNNING entry must remain — recovery must not overwrite it
        SchedulerJobLog log = schedulerJobLogRepository
                .findByJobNameAndJobDate(OrderGenerationService.JOB_NAME, missedDeliveryDate).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(SchedulerJobLog.JobStatus.RUNNING);
    }

    @Test
    void recovery_failedJobsAreRerun() throws Exception {
        // Clear any existing entry and insert FAILED
        clearJobLog(OrderGenerationService.JOB_NAME, missedDeliveryDate);
        markFailed(OrderGenerationService.JOB_NAME, missedDeliveryDate, "simulated failure");

        recovery.run(new DefaultApplicationArguments());

        SchedulerJobLog log = schedulerJobLogRepository
                .findByJobNameAndJobDate(OrderGenerationService.JOB_NAME, missedDeliveryDate).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(SchedulerJobLog.JobStatus.COMPLETED);
        assertThat(log.getErrorMessage()).isNull();
    }

    // ─── Recovery sequence ───────────────────────────────────────────────────

    @Test
    void recovery_missedOrderGeneration_createsOrders() throws Exception {
        factory.creditWallet(customer.getId(), 10_000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        // Simulate missed OrderGenerationJob by clearing its log entry
        clearJobLog(OrderGenerationService.JOB_NAME, missedDeliveryDate);
        // Also clear downstream jobs so they don't block
        clearJobLog(OrderFreezeService.JOB_NAME, missedDeliveryDate);
        clearJobLog(DeliverySheetScheduler.JOB_NAME, missedDeliveryDate);

        long orderCountBefore = orderRepository.count();
        recovery.run(new DefaultApplicationArguments());
        long orderCountAfter = orderRepository.count();

        assertThat(orderCountAfter).isGreaterThan(orderCountBefore);

        SchedulerJobLog genLog = schedulerJobLogRepository
                .findByJobNameAndJobDate(OrderGenerationService.JOB_NAME, missedDeliveryDate).orElseThrow();
        assertThat(genLog.getStatus()).isEqualTo(SchedulerJobLog.JobStatus.COMPLETED);
    }

    @Test
    void recovery_missedOrderFreeze_locksOrders() throws Exception {
        factory.creditWallet(customer.getId(), 10_000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        // Simulate missed OrderFreezeJob — clear generation too so recovery
        // regenerates orders for the subscription created in this test
        clearJobLog(OrderGenerationService.JOB_NAME, missedDeliveryDate);
        clearJobLog(OrderFreezeService.JOB_NAME, missedDeliveryDate);
        clearJobLog(DeliverySheetScheduler.JOB_NAME, missedDeliveryDate);

        recovery.run(new DefaultApplicationArguments());

        SchedulerJobLog freezeLog = schedulerJobLogRepository
                .findByJobNameAndJobDate(OrderFreezeService.JOB_NAME, missedDeliveryDate).orElseThrow();
        assertThat(freezeLog.getStatus()).isEqualTo(SchedulerJobLog.JobStatus.COMPLETED);

        List<Order> lockedOrders = orderRepository
                .findByDeliveryDateAndStatus(missedDeliveryDate, Order.OrderStatus.LOCKED);
        assertThat(lockedOrders).isNotEmpty();
    }

    @Test
    void recovery_missedDeliverySheet_generatesSnapshot() throws Exception {
        factory.creditWallet(customer.getId(), 10_000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        // Simulate missed DeliverySheetGenerationJob
        clearJobLog(DeliverySheetScheduler.JOB_NAME, missedDeliveryDate);

        recovery.run(new DefaultApplicationArguments());

        assertThat(snapshotRepository.existsByDeliveryDate(missedDeliveryDate)).isTrue();
    }

    // ─── Idempotency ─────────────────────────────────────────────────────────

    @Test
    void recovery_runTwice_isIdempotent() throws Exception {
        factory.creditWallet(customer.getId(), 10_000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        // First recovery (startup already ran, so this is a second run — all COMPLETED)
        recovery.run(new DefaultApplicationArguments());
        long orderCountAfterFirst = orderRepository.count();
        long recordCountAfterFirst = deliveryRecordRepository.count();
        long snapshotCountAfterFirst = snapshotRepository.count();

        // Second recovery — must be idempotent
        recovery.run(new DefaultApplicationArguments());
        long orderCountAfterSecond = orderRepository.count();
        long recordCountAfterSecond = deliveryRecordRepository.count();
        long snapshotCountAfterSecond = snapshotRepository.count();

        assertThat(orderCountAfterSecond).isEqualTo(orderCountAfterFirst);
        assertThat(recordCountAfterSecond).isEqualTo(recordCountAfterFirst);
        assertThat(snapshotCountAfterSecond).isEqualTo(snapshotCountAfterFirst);
    }

    @Test
    void recovery_noDuplicateOrders_afterMultipleRuns() throws Exception {
        factory.creditWallet(customer.getId(), 50_000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        // Clear all job logs to simulate full miss, then run 3 times
        clearAllJobLogsForPreviousDays();

        recovery.run(new DefaultApplicationArguments());
        recovery.run(new DefaultApplicationArguments());
        recovery.run(new DefaultApplicationArguments());

        // Each subscription should have at most one order per delivery date
        long orderCount = orderRepository
                .findByCustomerIdOrderByDeliveryDateDesc(customer.getId(), PageRequest.of(0, 100))
                .getTotalElements();
        // 3 days of recovery = at most 3 orders (one per delivery date), not 9
        assertThat(orderCount).isLessThanOrEqualTo(3);
    }

    @Test
    void recovery_noDuplicateDeliveryRecords_afterMultipleRuns() throws Exception {
        factory.creditWallet(customer.getId(), 50_000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        clearAllJobLogsForPreviousDays();

        recovery.run(new DefaultApplicationArguments());
        long recordCountAfterFirst = deliveryRecordRepository.count();

        recovery.run(new DefaultApplicationArguments());
        long recordCountAfterSecond = deliveryRecordRepository.count();

        assertThat(recordCountAfterSecond).isEqualTo(recordCountAfterFirst);
    }

    // ─── Chronological order ─────────────────────────────────────────────────

    @Test
    void recovery_checksThreePreviousDays() throws Exception {
        // Mark all jobs COMPLETED for 3 days ago and 2 days ago, leave 1 day ago missed
        LocalDate today = LocalDate.now(IST);

        for (int daysAgo = 3; daysAgo >= 2; daysAgo--) {
            LocalDate operationalDate = today.minusDays(daysAgo);
            LocalDate deliveryDate = operationalDate.plusDays(1);
            ensureCompleted(SubscriptionActivationService.JOB_NAME, operationalDate);
            ensureCompleted(OrderGenerationService.JOB_NAME, deliveryDate);
            ensureCompleted(OrderFreezeService.JOB_NAME, deliveryDate);
            ensureCompleted(DeliverySheetScheduler.JOB_NAME, deliveryDate);
        }

        // 1 day ago is missed — clear it
        LocalDate missedDate = today.minusDays(1).plusDays(1); // = today
        clearJobLog(OrderGenerationService.JOB_NAME, missedDate);
        clearJobLog(OrderFreezeService.JOB_NAME, missedDate);
        clearJobLog(DeliverySheetScheduler.JOB_NAME, missedDate);

        recovery.run(new DefaultApplicationArguments());

        // OrderGenerationJob for the missed date should now be COMPLETED
        SchedulerJobLog log = schedulerJobLogRepository
                .findByJobNameAndJobDate(OrderGenerationService.JOB_NAME, missedDate)
                .orElseThrow();
        assertThat(log.getStatus()).isEqualTo(SchedulerJobLog.JobStatus.COMPLETED);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Ensures a COMPLETED entry exists for the given job+date.
     * If one already exists (from startup recovery), leaves it.
     * If not, creates one.
     */
    private void ensureCompleted(String jobName, LocalDate date) {
        schedulerJobLogRepository.findByJobNameAndJobDate(jobName, date)
                .ifPresentOrElse(
                        existing -> {
                            if (existing.getStatus() != SchedulerJobLog.JobStatus.COMPLETED) {
                                existing.setStatus(SchedulerJobLog.JobStatus.COMPLETED);
                                existing.setFinishedAt(OffsetDateTime.now());
                                schedulerJobLogRepository.save(existing);
                            }
                        },
                        () -> {
                            SchedulerJobLog log = new SchedulerJobLog();
                            log.setJobName(jobName);
                            log.setJobDate(date);
                            log.setStatus(SchedulerJobLog.JobStatus.COMPLETED);
                            log.setFinishedAt(OffsetDateTime.now());
                            schedulerJobLogRepository.save(log);
                        }
                );
        schedulerJobLogRepository.flush();
    }

    /** Clears any existing job log entry for the given job+date. */
    private void clearJobLog(String jobName, LocalDate date) {
        schedulerJobLogRepository.findByJobNameAndJobDate(jobName, date)
                .ifPresent(existing -> {
                    schedulerJobLogRepository.delete(existing);
                    schedulerJobLogRepository.flush();
                });
    }

    private void markRunning(String jobName, LocalDate date) {
        SchedulerJobLog log = new SchedulerJobLog();
        log.setJobName(jobName);
        log.setJobDate(date);
        log.setStatus(SchedulerJobLog.JobStatus.RUNNING);
        schedulerJobLogRepository.save(log);
        schedulerJobLogRepository.flush();
    }

    private void markFailed(String jobName, LocalDate date, String errorMessage) {
        SchedulerJobLog log = new SchedulerJobLog();
        log.setJobName(jobName);
        log.setJobDate(date);
        log.setStatus(SchedulerJobLog.JobStatus.FAILED);
        log.setFinishedAt(OffsetDateTime.now());
        log.setErrorMessage(errorMessage);
        schedulerJobLogRepository.save(log);
        schedulerJobLogRepository.flush();
    }

    /** Clears all job log entries for the previous 3 days to simulate a full miss. */
    private void clearAllJobLogsForPreviousDays() {
        LocalDate today = LocalDate.now(IST);
        for (int daysAgo = 3; daysAgo >= 1; daysAgo--) {
            LocalDate operationalDate = today.minusDays(daysAgo);
            LocalDate deliveryDate = operationalDate.plusDays(1);
            clearJobLog(SubscriptionActivationService.JOB_NAME, operationalDate);
            clearJobLog(OrderGenerationService.JOB_NAME, deliveryDate);
            clearJobLog(OrderFreezeService.JOB_NAME, deliveryDate);
            clearJobLog(DeliverySheetScheduler.JOB_NAME, deliveryDate);
        }
    }
}
