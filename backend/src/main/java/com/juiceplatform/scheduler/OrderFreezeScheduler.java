package com.juiceplatform.scheduler;

import com.juiceplatform.service.OrderFreezeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Scheduled job for order freezing.
 * Runs at 22:00 IST daily (BR-SCH-01, BR-LCK-01).
 * Freezes SCHEDULED orders for tomorrow's delivery date.
 */
@Component
@RequiredArgsConstructor
public class OrderFreezeScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderFreezeScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OrderFreezeService orderFreezeService;

    @Scheduled(cron = "${scheduler.order-freeze.cron:0 0 22 * * *}", zone = "Asia/Kolkata")
    public void runOrderFreeze() {
        // Freeze orders for tomorrow's delivery date
        LocalDate deliveryDate = LocalDate.now(IST).plusDays(1);
        log.info("Scheduled OrderFreezeJob triggered for delivery date: {}", deliveryDate);

        try {
            OrderFreezeService.FreezeResult result = orderFreezeService.freezeOrdersForDate(deliveryDate);
            log.info("OrderFreezeJob completed: deliveryDate={}, locked={}, skipped={}",
                    result.deliveryDate(), result.ordersLocked(), result.duplicatesSkipped());
        } catch (Exception e) {
            log.error("OrderFreezeJob failed for delivery date {}: {}", deliveryDate, e.getMessage(), e);
        }
    }
}
