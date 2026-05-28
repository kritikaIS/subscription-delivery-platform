package com.juiceplatform.scheduler;

import com.juiceplatform.service.NotificationService;
import com.juiceplatform.service.SubscriptionActivationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Scheduled job for subscription activation.
 * Runs at 22:04 IST — before OrderGenerationJob (22:05) to ensure newly activated
 * subscriptions are included in the same night's order generation.
 * BR-SUB-05: PENDING_START → ACTIVE is scheduler-driven only.
 * BR-ORD-07: PENDING_START subscriptions are transitioned to ACTIVE before order generation.
 *
 * Note: activation is also called inline by OrderGenerationScheduler to ensure
 * correct sequencing when both run together (e.g. during startup recovery).
 */
@Component
@RequiredArgsConstructor
public class SubscriptionActivationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionActivationScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final SubscriptionActivationService subscriptionActivationService;
    private final NotificationService notificationService;

    @Scheduled(cron = "${scheduler.subscription-activation.cron:0 4 22 * * *}", zone = "Asia/Kolkata")
    public void runSubscriptionActivation() {
        LocalDate today = LocalDate.now(IST);
        log.info("Scheduled SubscriptionActivationJob triggered for date: {}", today);

        try {
            SubscriptionActivationService.ActivationResult result =
                    subscriptionActivationService.activateEligibleSubscriptions();

            log.info("SubscriptionActivationJob completed: date={}, activated={}",
                    result.date(), result.subscriptionsActivated());
        } catch (Exception e) {
            log.error("SubscriptionActivationJob failed for {}: {}", today, e.getMessage(), e);
            // Best-effort notification — non-blocking (BR-NOT-01, BR-NOT-03, BR-SCH-06)
            try {
                notificationService.notifySchedulerJobFailure(
                        SubscriptionActivationService.JOB_NAME, today, e.getMessage());
            } catch (Exception notifyEx) {
                log.warn("Failed to send scheduler failure notification: {}", notifyEx.getMessage());
            }
        }
    }
}
