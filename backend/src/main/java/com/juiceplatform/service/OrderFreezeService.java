package com.juiceplatform.service;

import com.juiceplatform.entity.DeliveryRecord;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.SchedulerJobLog;
import com.juiceplatform.repository.DeliveryRecordRepository;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.SchedulerJobLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Implements OrderFreezeJob logic.
 * Transitions SCHEDULED orders → LOCKED and creates delivery_record rows.
 * Wallet deduction does NOT happen here — it happens when admin marks DELIVERED (BR-DEL-04).
 */
@Service
@RequiredArgsConstructor
public class OrderFreezeService {

    private static final Logger log = LoggerFactory.getLogger(OrderFreezeService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final String JOB_NAME = "OrderFreezeJob";

    private final OrderRepository orderRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final SchedulerJobLogRepository schedulerJobLogRepository;

    /**
     * Freezes all SCHEDULED orders for the given delivery date.
     * Idempotent: skips orders that already have a delivery_record (already locked).
     * Scheduler job log idempotency: rejects concurrent runs, allows reruns of COMPLETED/FAILED.
     */
    @Transactional
    public FreezeResult freezeOrdersForDate(LocalDate deliveryDate) {
        log.info("OrderFreezeJob starting for delivery date: {}", deliveryDate);

        // Scheduler job log idempotency (BR-SCH-02)
        SchedulerJobLog jobLog = acquireJobLog(deliveryDate);
        if (jobLog == null) {
            log.warn("OrderFreezeJob rejected for {} — another instance is RUNNING", deliveryDate);
            return new FreezeResult(deliveryDate, 0, 0, false);
        }

        int ordersLocked = 0;
        int duplicatesSkipped = 0;

        try {
            // Find all SCHEDULED orders for this delivery date (BR-LCK-01)
            List<Order> scheduledOrders = orderRepository
                    .findByDeliveryDateAndStatus(deliveryDate, Order.OrderStatus.SCHEDULED);

            for (Order order : scheduledOrders) {
                // Idempotency: skip if delivery_record already exists for this order
                if (deliveryRecordRepository.existsByOrderId(order.getId())) {
                    duplicatesSkipped++;
                    continue;
                }

                // Transition order: SCHEDULED → LOCKED (BR-LCK-01)
                order.setStatus(Order.OrderStatus.LOCKED);
                orderRepository.save(order);

                // Create delivery_record with status=PENDING (BR-LCK-06)
                DeliveryRecord record = new DeliveryRecord();
                record.setOrderId(order.getId());
                record.setDeliveryDate(order.getDeliveryDate());
                record.setDeliveryWindow("Morning");
                record.setStatus(DeliveryRecord.DeliveryRecordStatus.PENDING);
                deliveryRecordRepository.save(record);

                ordersLocked++;
            }

            // Count already-LOCKED orders for this date that have delivery records.
            // These were locked by a previous run — they are idempotent duplicates (BR-SCH-02).
            List<Order> alreadyLockedOrders = orderRepository
                    .findByDeliveryDateAndStatus(deliveryDate, Order.OrderStatus.LOCKED);
            for (Order order : alreadyLockedOrders) {
                if (deliveryRecordRepository.existsByOrderId(order.getId())) {
                    duplicatesSkipped++;
                }
            }

            // Mark job COMPLETED
            jobLog.setStatus(SchedulerJobLog.JobStatus.COMPLETED);
            jobLog.setFinishedAt(OffsetDateTime.now(IST));
            jobLog.setRowsProcessed(ordersLocked);
            schedulerJobLogRepository.save(jobLog);

            log.info("OrderFreezeJob completed for {}: {} orders locked, {} duplicates skipped",
                    deliveryDate, ordersLocked, duplicatesSkipped);

        } catch (Exception e) {
            // Mark job FAILED
            jobLog.setStatus(SchedulerJobLog.JobStatus.FAILED);
            jobLog.setFinishedAt(OffsetDateTime.now(IST));
            jobLog.setErrorMessage(e.getMessage());
            schedulerJobLogRepository.save(jobLog);

            log.error("OrderFreezeJob failed for {}: {}", deliveryDate, e.getMessage(), e);
            throw e;
        }

        return new FreezeResult(deliveryDate, ordersLocked, duplicatesSkipped, true);
    }

    /**
     * Acquires a scheduler_job_log entry for this job run.
     * Returns null if a RUNNING entry already exists (concurrent guard).
     * Deletes and recreates if COMPLETED or FAILED (allows rerun).
     */
    private SchedulerJobLog acquireJobLog(LocalDate deliveryDate) {
        schedulerJobLogRepository.findByJobNameAndJobDate(JOB_NAME, deliveryDate)
                .ifPresent(existing -> {
                    if (existing.getStatus() == SchedulerJobLog.JobStatus.RUNNING) {
                        throw new IllegalStateException(
                                "OrderFreezeJob is already RUNNING for " + deliveryDate);
                    }
                    // COMPLETED or FAILED → delete to allow rerun
                    schedulerJobLogRepository.delete(existing);
                    schedulerJobLogRepository.flush();
                });

        SchedulerJobLog jobLog = new SchedulerJobLog();
        jobLog.setJobName(JOB_NAME);
        jobLog.setJobDate(deliveryDate);
        jobLog.setStatus(SchedulerJobLog.JobStatus.RUNNING);
        return schedulerJobLogRepository.save(jobLog);
    }

    public record FreezeResult(
            LocalDate deliveryDate,
            int ordersLocked,
            int duplicatesSkipped,
            boolean completed
    ) {}
}
