package com.juiceplatform.scheduler;

import com.juiceplatform.entity.DeliverySheetSnapshot;
import com.juiceplatform.entity.SchedulerJobLog;
import com.juiceplatform.repository.SchedulerJobLogRepository;
import com.juiceplatform.service.DeliverySheetService;
import com.juiceplatform.service.NotificationService;
import com.juiceplatform.service.OrderFreezeService;
import com.juiceplatform.service.OrderGenerationService;
import com.juiceplatform.service.SubscriptionActivationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Startup recovery for missed scheduler jobs (BR-SCH-04).
 *
 * On application startup, inspects the previous 3 calendar days in Asia/Kolkata timezone.
 * For each missed day, reruns jobs in the required sequence:
 *   1. SubscriptionActivationJob (prerequisite for OrderGenerationJob)
 *   2. OrderGenerationJob
 *   3. OrderFreezeJob
 *   4. DeliverySheetGenerationJob
 *
 * A job is considered "missed" if no scheduler_job_log entry exists for (job_name, job_date),
 * OR if the existing entry has status = FAILED (BR-SCH-02: FAILED → rerun allowed).
 * A RUNNING entry is skipped (concurrent guard).
 * A COMPLETED entry is skipped (already done).
 *
 * All recovery runs are fully idempotent — existing orders, delivery_records, and snapshots
 * are not duplicated.
 */
@Component
@RequiredArgsConstructor
public class SchedulerStartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchedulerStartupRecovery.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int RECOVERY_DAYS = 3;

    private final SchedulerJobLogRepository schedulerJobLogRepository;
    private final SubscriptionActivationService subscriptionActivationService;
    private final OrderGenerationService orderGenerationService;
    private final OrderFreezeService orderFreezeService;
    private final DeliverySheetService deliverySheetService;
    private final NotificationService notificationService;

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(IST);
        log.info("SchedulerStartupRecovery: checking previous {} days for missed jobs", RECOVERY_DAYS);

        // Check days from oldest to newest (chronological order per BR-SCH-04)
        for (int daysAgo = RECOVERY_DAYS; daysAgo >= 1; daysAgo--) {
            LocalDate missedDate = today.minusDays(daysAgo);
            // The operational delivery date for a given night is the next day
            LocalDate deliveryDate = missedDate.plusDays(1);

            log.debug("Checking missed jobs for operational date {} (delivery date {})",
                    missedDate, deliveryDate);

            recoverDayInSequence(missedDate, deliveryDate);
        }

        log.info("SchedulerStartupRecovery: completed");
    }

    /**
     * Recovers all missed jobs for a single operational date in the required sequence.
     * Sequence per BR-SCH-04: SubscriptionActivation → OrderGeneration → OrderFreeze → DeliverySheet
     */
    private void recoverDayInSequence(LocalDate operationalDate, LocalDate deliveryDate) {
        // Step 1: SubscriptionActivation (prerequisite for OrderGeneration)
        if (needsRecovery(SubscriptionActivationService.JOB_NAME, operationalDate)) {
            log.info("Recovering SubscriptionActivationJob for date {}", operationalDate);
            try {
                subscriptionActivationService.activateEligibleSubscriptions();
            } catch (Exception e) {
                log.error("Recovery of SubscriptionActivationJob for {} failed: {}",
                        operationalDate, e.getMessage(), e);
                notifyFailureSafe(SubscriptionActivationService.JOB_NAME, operationalDate, e.getMessage());
                // Continue — activation failure should not block order generation recovery
            }
        }

        // Step 2: OrderGeneration
        if (needsRecovery(OrderGenerationService.JOB_NAME, deliveryDate)) {
            log.info("Recovering OrderGenerationJob for delivery date {}", deliveryDate);
            try {
                orderGenerationService.generateOrdersForDate(deliveryDate);
            } catch (Exception e) {
                log.error("Recovery of OrderGenerationJob for {} failed: {}",
                        deliveryDate, e.getMessage(), e);
                notifyFailureSafe(OrderGenerationService.JOB_NAME, deliveryDate, e.getMessage());
                // If generation fails, freeze and delivery sheet cannot proceed meaningfully
                return;
            }
        }

        // Step 3: OrderFreeze
        if (needsRecovery(OrderFreezeService.JOB_NAME, deliveryDate)) {
            log.info("Recovering OrderFreezeJob for delivery date {}", deliveryDate);
            try {
                orderFreezeService.freezeOrdersForDate(deliveryDate);
            } catch (Exception e) {
                log.error("Recovery of OrderFreezeJob for {} failed: {}",
                        deliveryDate, e.getMessage(), e);
                notifyFailureSafe(OrderFreezeService.JOB_NAME, deliveryDate, e.getMessage());
                // If freeze fails, delivery sheet cannot be generated correctly
                return;
            }
        }

        // Step 4: DeliverySheet
        if (needsRecovery(DeliverySheetScheduler.JOB_NAME, deliveryDate)) {
            log.info("Recovering DeliverySheetGenerationJob for delivery date {}", deliveryDate);
            try {
                deliverySheetService.generateSnapshot(
                        deliveryDate, DeliverySheetSnapshot.GeneratedBySource.SCHEDULER, null);
            } catch (Exception e) {
                log.error("Recovery of DeliverySheetGenerationJob for {} failed: {}",
                        deliveryDate, e.getMessage(), e);
                notifyFailureSafe(DeliverySheetScheduler.JOB_NAME, deliveryDate, e.getMessage());
            }
        }
    }

    /**
     * Returns true if the job needs to be recovered for the given date.
     * A job needs recovery if:
     * - No scheduler_job_log entry exists (missed entirely), OR
     * - The existing entry has status = FAILED (BR-SCH-02: FAILED → rerun allowed)
     *
     * Returns false if:
     * - Status = COMPLETED (already done — skip)
     * - Status = RUNNING (concurrent guard — skip, another instance may be running)
     */
    private boolean needsRecovery(String jobName, LocalDate date) {
        Optional<SchedulerJobLog> existing =
                schedulerJobLogRepository.findByJobNameAndJobDate(jobName, date);

        if (existing.isEmpty()) {
            log.debug("Job {} for date {} has no log entry — needs recovery", jobName, date);
            return true;
        }

        SchedulerJobLog.JobStatus status = existing.get().getStatus();
        if (status == SchedulerJobLog.JobStatus.FAILED) {
            log.info("Job {} for date {} has status FAILED — will rerun", jobName, date);
            return true;
        }
        if (status == SchedulerJobLog.JobStatus.RUNNING) {
            log.warn("Job {} for date {} is RUNNING — skipping recovery (concurrent guard)", jobName, date);
            return false;
        }
        // COMPLETED — already done
        log.debug("Job {} for date {} is COMPLETED — skipping", jobName, date);
        return false;
    }

    /**
     * Sends a failure notification without throwing — notifications are best-effort (BR-NOT-01).
     */
    private void notifyFailureSafe(String jobName, LocalDate date, String errorMessage) {
        try {
            notificationService.notifySchedulerJobFailure(jobName, date, errorMessage);
        } catch (Exception e) {
            log.warn("Failed to send scheduler failure notification for {} on {}: {}",
                    jobName, date, e.getMessage());
        }
    }
}
