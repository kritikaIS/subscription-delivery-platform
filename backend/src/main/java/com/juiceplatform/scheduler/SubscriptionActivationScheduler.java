package com.juiceplatform.scheduler;

import com.juiceplatform.service.SubscriptionActivationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for subscription activation.
 * Runs before OrderGenerationJob to ensure newly activated subscriptions
 * are included in the same night's order generation.
 * BR-SUB-05: PENDING_START → ACTIVE is scheduler-driven only.
 * BR-ORD-07: PENDING_START subscriptions are transitioned to ACTIVE before order generation.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionActivationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionActivationScheduler.class);

    private final SubscriptionActivationService subscriptionActivationService;

    @Scheduled(cron = "${scheduler.subscription-activation.cron:0 4 22 * * *}", zone = "Asia/Kolkata")
    public void runSubscriptionActivation() {
        log.info("Scheduled SubscriptionActivationJob triggered");

        try {
            SubscriptionActivationService.ActivationResult result =
                    subscriptionActivationService.activateEligibleSubscriptions();

            log.info("SubscriptionActivationJob completed: date={}, activated={}",
                    result.date(), result.subscriptionsActivated());
        } catch (Exception e) {
            log.error("SubscriptionActivationJob failed: {}", e.getMessage(), e);
        }
    }
}
