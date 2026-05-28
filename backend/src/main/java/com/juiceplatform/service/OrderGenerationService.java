package com.juiceplatform.service;

import com.juiceplatform.entity.DeliveryAddress;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.SchedulerJobLog;
import com.juiceplatform.entity.Subscription;
import com.juiceplatform.entity.SubscriptionChangeRequest;
import com.juiceplatform.entity.WalletLedger;
import com.juiceplatform.repository.DeliveryAddressRepository;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.ProductRepository;
import com.juiceplatform.repository.SchedulerJobLogRepository;
import com.juiceplatform.repository.SubscriptionChangeRequestRepository;
import com.juiceplatform.repository.SubscriptionRepository;
import com.juiceplatform.repository.WalletLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderGenerationService {

    private static final Logger log = LoggerFactory.getLogger(OrderGenerationService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final String JOB_NAME = "OrderGenerationJob";

    private final SubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final BusinessHolidayService businessHolidayService;
    private final NotificationService notificationService;
    private final SubscriptionChangeRequestRepository changeRequestRepository;
    private final SchedulerJobLogRepository schedulerJobLogRepository;

    /**
     * Generates orders for the given delivery date.
     * Tracked in scheduler_job_log (BR-SCH-03).
     * Idempotent: duplicate orders are prevented by idempotency key (BR-ORD-04).
     * Concurrent RUNNING guard: rejects if a RUNNING entry already exists (BR-SCH-02).
     */
    @Transactional
    public OrderGenerationResult generateOrdersForDate(LocalDate deliveryDate) {
        // Skip order generation for holidays (BR-ORD-03, BR-HOL-02)
        if (businessHolidayService.isHoliday(deliveryDate)) {
            log.info("OrderGenerationJob skipped for {} — configured as a business holiday", deliveryDate);
            return new OrderGenerationResult(deliveryDate, 0, 0, 0);
        }

        // Acquire scheduler_job_log entry — rejects concurrent RUNNING, allows rerun of COMPLETED/FAILED
        SchedulerJobLog jobLog = acquireJobLog(deliveryDate);
        if (jobLog == null) {
            log.warn("OrderGenerationJob rejected for {} — another instance is RUNNING", deliveryDate);
            return new OrderGenerationResult(deliveryDate, 0, 0, 0);
        }

        log.info("Starting order generation for delivery date: {}", deliveryDate);

        // Find all ACTIVE subscriptions (BR-ORD-02)
        List<Subscription> activeSubscriptions = subscriptionRepository
                .findAllByStatus(Subscription.SubscriptionStatus.ACTIVE);

        int ordersCreated = 0;
        int duplicatesSkipped = 0;

        try {
            for (Subscription subscription : activeSubscriptions) {
                // Apply any APPROVED change requests effective on or before deliveryDate (BR-SUB-10/11)
                applyChangeRequests(subscription, deliveryDate);

                // Build idempotency key: sub_<id>_<YYYY-MM-DD> (BR-ORD-04)
                String idempotencyKey = "sub_" + subscription.getId() + "_" + deliveryDate;

                // Check for duplicate (idempotency)
                if (orderRepository.existsByIdempotencyKey(idempotencyKey)) {
                    duplicatesSkipped++;
                    continue;
                }

                // Load product for price snapshot (BR-ORD-06)
                Product product = productRepository.findById(subscription.getProductId()).orElse(null);
                if (product == null || !product.getIsAvailable()) {
                    log.warn("Skipping subscription {} — product {} unavailable",
                            subscription.getId(), subscription.getProductId());
                    continue;
                }

                // Load customer address for snapshot (BR-ONB-04)
                DeliveryAddress address = deliveryAddressRepository
                        .findByCustomerId(subscription.getCustomerId()).orElse(null);
                if (address == null) {
                    log.warn("Skipping subscription {} — no delivery address for customer {}",
                            subscription.getId(), subscription.getCustomerId());
                    continue;
                }

                long orderCost = product.getPricePerUnitPaise() * subscription.getQuantity();

                // Wallet balance check (BR-WAL-10 / BR-ORD-05)
                long walletBalance = walletLedgerRepository
                        .findTopByCustomerIdOrderByCreatedAtDesc(subscription.getCustomerId())
                        .map(WalletLedger::getRunningBalancePaise)
                        .orElse(0L);

                if (walletBalance < orderCost) {
                    log.warn("Skipping subscription {} — insufficient wallet balance ({} < {})",
                            subscription.getId(), walletBalance, orderCost);
                    // Notify customer and admin — best-effort, after transaction (BR-NOT-01, BR-NOT-02, BR-NOT-03)
                    notificationService.notifyOrderGenerationBlocked(
                            subscription.getCustomerId(), "Customer", walletBalance, orderCost);
                    continue;
                }

                // Low balance warning check (BR-WAL-09): balance < ₹200 = 20,000 paise
                if (walletBalance < 20_000L) {
                    notificationService.notifyLowBalance(
                            subscription.getCustomerId(), "Customer", walletBalance, 20_000L);
                }

                // Create order with snapshots
                Order order = new Order();
                order.setCustomerId(subscription.getCustomerId());
                order.setSubscriptionId(subscription.getId());
                order.setProductId(product.getId());
                order.setDeliveryLine1(address.getLine1());
                order.setDeliveryLine2(address.getLine2());
                order.setDeliveryCity(address.getCity());
                order.setDeliveryState(address.getState());
                order.setDeliveryPincode(address.getPincode());
                order.setDeliveryNotes(address.getDeliveryNotes());
                order.setDeliveryDate(deliveryDate);
                order.setQuantity(subscription.getQuantity());
                order.setUnitPricePaise(product.getPricePerUnitPaise());
                order.setTotalAmountPaise(orderCost);
                order.setStatus(Order.OrderStatus.SCHEDULED);
                order.setIdempotencyKey(idempotencyKey);

                orderRepository.save(order);
                ordersCreated++;
            }

            // Mark job COMPLETED
            jobLog.setStatus(SchedulerJobLog.JobStatus.COMPLETED);
            jobLog.setFinishedAt(OffsetDateTime.now(IST));
            jobLog.setRowsProcessed(ordersCreated);
            schedulerJobLogRepository.save(jobLog);

            log.info("Order generation complete for {}: {} active subscriptions processed, {} orders created, {} duplicates skipped",
                    deliveryDate, activeSubscriptions.size(), ordersCreated, duplicatesSkipped);

        } catch (Exception e) {
            // Mark job FAILED
            jobLog.setStatus(SchedulerJobLog.JobStatus.FAILED);
            jobLog.setFinishedAt(OffsetDateTime.now(IST));
            jobLog.setErrorMessage(e.getMessage());
            schedulerJobLogRepository.save(jobLog);

            log.error("OrderGenerationJob failed for {}: {}", deliveryDate, e.getMessage(), e);
            throw e;
        }

        return new OrderGenerationResult(deliveryDate, activeSubscriptions.size(), ordersCreated, duplicatesSkipped);
    }

    /**
     * Acquires a scheduler_job_log entry for this job run.
     * Returns null if a RUNNING entry already exists (concurrent guard).
     * Deletes and recreates if COMPLETED or FAILED (allows rerun).
     */
    private SchedulerJobLog acquireJobLog(LocalDate deliveryDate) {
        try {
            schedulerJobLogRepository.findByJobNameAndJobDate(JOB_NAME, deliveryDate)
                    .ifPresent(existing -> {
                        if (existing.getStatus() == SchedulerJobLog.JobStatus.RUNNING) {
                            throw new IllegalStateException(
                                    "OrderGenerationJob is already RUNNING for " + deliveryDate);
                        }
                        // COMPLETED or FAILED → delete to allow rerun
                        schedulerJobLogRepository.delete(existing);
                        schedulerJobLogRepository.flush();
                    });
        } catch (IllegalStateException e) {
            // RUNNING guard — return null to signal rejection
            return null;
        }

        SchedulerJobLog jobLog = new SchedulerJobLog();
        jobLog.setJobName(JOB_NAME);
        jobLog.setJobDate(deliveryDate);
        jobLog.setStatus(SchedulerJobLog.JobStatus.RUNNING);
        return schedulerJobLogRepository.save(jobLog);
    }

    /**
     * Applies all APPROVED change requests for a subscription whose effective_date <= deliveryDate.
     * Mutates the subscription in-memory (and persists it) and marks requests APPLIED.
     * If a SCHEDULED order already exists for deliveryDate, it is updated inline (BR-SUB-11).
     * LOCKED/DELIVERED/SKIPPED/CANCELLED orders are never touched.
     */
    private void applyChangeRequests(Subscription subscription, LocalDate deliveryDate) {
        List<SubscriptionChangeRequest> dueRequests = changeRequestRepository
                .findBySubscriptionIdAndStatusAndEffectiveDateLessThanEqual(
                        subscription.getId(),
                        SubscriptionChangeRequest.ChangeRequestStatus.APPROVED,
                        deliveryDate);

        if (dueRequests.isEmpty()) {
            return;
        }

        boolean subscriptionMutated = false;

        for (SubscriptionChangeRequest req : dueRequests) {
            if (req.getChangeType() == SubscriptionChangeRequest.ChangeRequestType.QUANTITY) {
                int newQuantity = Integer.parseInt(req.getNewValue());
                log.info("Applying QUANTITY change request {} to subscription {} — {} → {}",
                        req.getId(), subscription.getId(), subscription.getQuantity(), newQuantity);
                subscription.setQuantity(newQuantity);
                subscriptionMutated = true;

            } else if (req.getChangeType() == SubscriptionChangeRequest.ChangeRequestType.PRODUCT) {
                java.util.UUID newProductId = java.util.UUID.fromString(req.getNewValue());
                Product newProduct = productRepository.findById(newProductId).orElse(null);
                if (newProduct == null || !newProduct.getIsAvailable()) {
                    log.warn("Skipping PRODUCT change request {} — product {} unavailable",
                            req.getId(), newProductId);
                    // Do not mark APPLIED; leave APPROVED so it can be retried or manually resolved
                    continue;
                }
                log.info("Applying PRODUCT change request {} to subscription {} — {} → {}",
                        req.getId(), subscription.getId(), subscription.getProductId(), newProductId);
                subscription.setProductId(newProductId);
                subscriptionMutated = true;
            }

            req.setStatus(SubscriptionChangeRequest.ChangeRequestStatus.APPLIED);
            changeRequestRepository.save(req);
        }

        if (subscriptionMutated) {
            subscriptionRepository.save(subscription);

            // If a SCHEDULED order already exists for deliveryDate, update it inline (BR-SUB-11)
            String idempotencyKey = "sub_" + subscription.getId() + "_" + deliveryDate;
            orderRepository.findByIdempotencyKeyAndStatus(idempotencyKey, Order.OrderStatus.SCHEDULED)
                    .ifPresent(existingOrder -> {
                        Product product = productRepository.findById(subscription.getProductId()).orElse(null);
                        if (product != null) {
                            existingOrder.setQuantity(subscription.getQuantity());
                            existingOrder.setProductId(product.getId());
                            existingOrder.setUnitPricePaise(product.getPricePerUnitPaise());
                            existingOrder.setTotalAmountPaise(
                                    product.getPricePerUnitPaise() * subscription.getQuantity());
                            orderRepository.save(existingOrder);
                            log.info("Updated existing SCHEDULED order {} for subscription {} on {}",
                                    existingOrder.getId(), subscription.getId(), deliveryDate);
                        }
                    });
        }
    }

    public record OrderGenerationResult(
            LocalDate deliveryDate,
            int activeSubscriptionsProcessed,
            int ordersCreated,
            int duplicatesSkipped
    ) {}
}
