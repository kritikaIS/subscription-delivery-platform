package com.juiceplatform.scheduler;

import com.juiceplatform.entity.DeliverySheetSnapshot;
import com.juiceplatform.service.DeliverySheetService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Scheduled job for delivery sheet generation.
 * Runs at 22:10 IST daily (BR-SCH-01) — after OrderFreezeJob (22:00).
 * Generates snapshot for tomorrow's delivery date.
 */
@Component
@RequiredArgsConstructor
public class DeliverySheetScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliverySheetScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DeliverySheetService deliverySheetService;

    @Scheduled(cron = "${scheduler.delivery-sheet.cron:0 10 22 * * *}", zone = "Asia/Kolkata")
    public void runDeliverySheetGeneration() {
        LocalDate deliveryDate = LocalDate.now(IST).plusDays(1);
        log.info("Scheduled DeliverySheetGenerationJob triggered for delivery date: {}", deliveryDate);

        try {
            deliverySheetService.generateSnapshot(deliveryDate,
                    DeliverySheetSnapshot.GeneratedBySource.SCHEDULER, null);
        } catch (Exception e) {
            log.error("DeliverySheetGenerationJob failed for {}: {}", deliveryDate, e.getMessage(), e);
        }
    }
}
