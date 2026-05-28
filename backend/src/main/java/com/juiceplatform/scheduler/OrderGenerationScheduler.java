package com.juiceplatform.scheduler;

import com.juiceplatform.service.OrderGenerationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Scheduled job for daily order generation.
 * Runs at 22:05 IST daily (BR-SCH-01).
 * Generates orders for the next operational delivery date (tomorrow).
 */
@Component
@RequiredArgsConstructor
public class OrderGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderGenerationScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OrderGenerationService orderGenerationService;

    @Scheduled(cron = "${scheduler.order-generation.cron:0 5 22 * * *}", zone = "Asia/Kolkata")
    public void runOrderGeneration() {
        LocalDate deliveryDate = LocalDate.now(IST).plusDays(1);
        log.info("Scheduled OrderGenerationJob triggered for delivery date: {}", deliveryDate);

        try {
            OrderGenerationService.OrderGenerationResult result =
                    orderGenerationService.generateOrdersForDate(deliveryDate);

            log.info("OrderGenerationJob completed: deliveryDate={}, subscriptions={}, created={}, skipped={}",
                    result.deliveryDate(), result.activeSubscriptionsProcessed(),
                    result.ordersCreated(), result.duplicatesSkipped());
        } catch (Exception e) {
            log.error("OrderGenerationJob failed for delivery date {}: {}", deliveryDate, e.getMessage(), e);
        }
    }
}
