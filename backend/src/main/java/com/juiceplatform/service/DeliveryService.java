package com.juiceplatform.service;

import com.juiceplatform.dto.delivery.MarkDeliveredResponse;
import com.juiceplatform.dto.delivery.MarkSkippedResponse;
import com.juiceplatform.entity.DeliveryRecord;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.WalletLedger;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.DeliveryRecordRepository;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.WalletLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Handles delivery execution: mark DELIVERED or SKIPPED.
 * Wallet deduction occurs ONLY on DELIVERED, inside the same transaction (BR-DEL-04).
 * No wallet deduction for SKIPPED (BR-DEL-05).
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OrderRepository orderRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final AuditLogService auditLogService;

    /**
     * Marks a LOCKED order as DELIVERED.
     * Atomically:
     *   1. Updates order.status = DELIVERED
     *   2. Updates delivery_record.status = DELIVERED, delivered_at = now
     *   3. Inserts DEBIT wallet_ledger entry (source_type = DELIVERY_DEBIT)
     *
     * Idempotent: if already DELIVERED, returns existing state without a second debit.
     * (BR-DEL-04, BR-LCK-06, BR-WAL-03)
     */
    @Transactional
    public MarkDeliveredResponse markDelivered(UUID orderId, UUID adminId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Order not found: " + orderId, HttpStatus.NOT_FOUND));

        // Idempotency: already DELIVERED — return existing state, no second debit
        if (order.getStatus() == Order.OrderStatus.DELIVERED) {
            DeliveryRecord record = deliveryRecordRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                            "Delivery record not found for order: " + orderId, HttpStatus.NOT_FOUND));

            long currentBalance = walletLedgerRepository.findTopByCustomerIdOrderByCreatedAtDesc(order.getCustomerId())
                    .map(WalletLedger::getRunningBalancePaise)
                    .orElse(0L);

            return MarkDeliveredResponse.builder()
                    .orderId(order.getId())
                    .status(order.getStatus().name())
                    .amountDeductedPaise(order.getTotalAmountPaise())
                    .newWalletBalancePaise(currentBalance)
                    .deliveredAt(record.getDeliveredAt())
                    .build();
        }

        // Only LOCKED orders can be marked delivered (BR-LCK-02)
        if (order.getStatus() != Order.OrderStatus.LOCKED) {
            throw new BusinessException("ORDER_NOT_DELIVERABLE",
                    "Order must be in LOCKED state to mark as delivered", HttpStatus.CONFLICT);
        }

        // Check wallet balance — acquire pessimistic write lock on latest ledger row
        // to prevent concurrent delivery confirmations from computing the same balance (db-schema §6.1)
        long currentBalance = walletLedgerRepository.findTopByCustomerIdForUpdate(order.getCustomerId())
                .map(WalletLedger::getRunningBalancePaise)
                .orElse(0L);

        if (currentBalance < order.getTotalAmountPaise()) {
            throw new BusinessException("INSUFFICIENT_BALANCE",
                    "Wallet balance is insufficient for this delivery", HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now(IST);
        long newBalance = currentBalance - order.getTotalAmountPaise();

        // 1. Update order status
        order.setStatus(Order.OrderStatus.DELIVERED);
        orderRepository.save(order);

        // 2. Update delivery_record
        DeliveryRecord record = deliveryRecordRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Delivery record not found for order: " + orderId, HttpStatus.NOT_FOUND));
        record.setStatus(DeliveryRecord.DeliveryRecordStatus.DELIVERED);
        record.setDeliveredAt(now);
        deliveryRecordRepository.save(record);

        // 3. Insert DEBIT ledger entry (BR-DEL-04, BR-WAL-04)
        // Idempotency enforced at DB level by uq_wallet_ledger_order_source (order_id, source_type)
        WalletLedger ledgerEntry = new WalletLedger();
        ledgerEntry.setCustomerId(order.getCustomerId());
        ledgerEntry.setOrderId(order.getId());
        ledgerEntry.setEntryType(WalletLedger.EntryType.DEBIT);
        ledgerEntry.setSourceType(WalletLedger.SourceType.DELIVERY_DEBIT);
        ledgerEntry.setAmountPaise(order.getTotalAmountPaise());
        ledgerEntry.setRunningBalancePaise(newBalance);
        ledgerEntry.setDescription("Delivery on " + order.getDeliveryDate()
                + " — " + order.getQuantity() + " unit(s)");
        ledgerEntry.setCreatedByUserId(adminId);
        walletLedgerRepository.save(ledgerEntry);

        // Audit log — action_type: ORDER_OVERRIDE (BR-AUD-01)
        auditLogService.log("ORDER_OVERRIDE", "order", orderId.toString(),
                java.util.Map.of("status", "LOCKED"),
                java.util.Map.of("status", "DELIVERED", "amountDeductedPaise", order.getTotalAmountPaise()),
                adminId);

        return MarkDeliveredResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus().name())
                .amountDeductedPaise(order.getTotalAmountPaise())
                .newWalletBalancePaise(newBalance)
                .deliveredAt(now)
                .build();
    }

    /**
     * Marks a LOCKED order as SKIPPED.
     * Updates order.status and delivery_record.status.
     * NO wallet deduction (BR-DEL-05).
     * Idempotent: if already SKIPPED, returns existing state.
     */
    @Transactional
    public MarkSkippedResponse markSkipped(UUID orderId, String skipReasonStr, UUID adminId) {
        // Validate skip reason against the enum defined in docs
        Order.SkipReason skipReason;
        try {
            skipReason = Order.SkipReason.valueOf(skipReasonStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_SKIP_REASON",
                    "skipReason must be one of: CUSTOMER_UNAVAILABLE, PRODUCT_UNAVAILABLE, DAMAGED, OTHER",
                    HttpStatus.BAD_REQUEST);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Order not found: " + orderId, HttpStatus.NOT_FOUND));

        // Idempotency: already SKIPPED — return existing state
        if (order.getStatus() == Order.OrderStatus.SKIPPED) {
            DeliveryRecord record = deliveryRecordRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                            "Delivery record not found for order: " + orderId, HttpStatus.NOT_FOUND));

            return MarkSkippedResponse.builder()
                    .orderId(order.getId())
                    .status(order.getStatus().name())
                    .skipReason(order.getSkipReason() != null ? order.getSkipReason().name() : null)
                    .skippedAt(record.getDeliveredAt() != null ? record.getDeliveredAt() : OffsetDateTime.now(IST))
                    .build();
        }

        // Only LOCKED orders can be skipped
        if (order.getStatus() != Order.OrderStatus.LOCKED) {
            throw new BusinessException("ORDER_NOT_SKIPPABLE",
                    "Order must be in LOCKED state to mark as skipped", HttpStatus.CONFLICT);
        }

        OffsetDateTime now = OffsetDateTime.now(IST);

        // 1. Update order status and skip_reason
        order.setStatus(Order.OrderStatus.SKIPPED);
        order.setSkipReason(skipReason);
        orderRepository.save(order);

        // 2. Update delivery_record
        DeliveryRecord record = deliveryRecordRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Delivery record not found for order: " + orderId, HttpStatus.NOT_FOUND));
        record.setStatus(DeliveryRecord.DeliveryRecordStatus.SKIPPED);
        record.setSkipReason(skipReason);
        deliveryRecordRepository.save(record);

        // Audit log — action_type: MANUAL_STATUS_CORRECTION (BR-AUD-01)
        auditLogService.log("MANUAL_STATUS_CORRECTION", "order", orderId.toString(),
                java.util.Map.of("status", "LOCKED"),
                java.util.Map.of("status", "SKIPPED", "skipReason", skipReason.name()),
                adminId);

        return MarkSkippedResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus().name())
                .skipReason(skipReason.name())
                .skippedAt(now)
                .build();
    }
}
