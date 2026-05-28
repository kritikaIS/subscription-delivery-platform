package com.juiceplatform.service;

import com.juiceplatform.entity.Subscription;
import com.juiceplatform.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Activates PENDING_START subscriptions whose effective start date has been reached.
 * BR-SUB-05: PENDING_START → ACTIVE transition is scheduler-driven only.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionActivationService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionActivationService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public ActivationResult activateEligibleSubscriptions() {
        LocalDate today = LocalDate.now(IST);
        log.info("Starting subscription activation for date: {}", today);

        List<Subscription> pendingSubscriptions = subscriptionRepository
                .findAllByStatusAndStartDateLessThanEqual(
                        Subscription.SubscriptionStatus.PENDING_START, today);

        int activated = 0;
        for (Subscription subscription : pendingSubscriptions) {
            subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(subscription);
            activated++;
            log.debug("Activated subscription: {} (customer: {}, product: {})",
                    subscription.getId(), subscription.getCustomerId(), subscription.getProductId());
        }

        log.info("Subscription activation complete: {} subscriptions activated", activated);
        return new ActivationResult(today, activated);
    }

    public record ActivationResult(LocalDate date, int subscriptionsActivated) {}
}
