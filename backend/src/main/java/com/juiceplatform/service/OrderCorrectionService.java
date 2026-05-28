package com.juiceplatform.service;

import com.juiceplatform.dto.delivery.OrderCorrectionRequest;
import com.juiceplatform.dto.delivery.OrderCorrectionResponse;
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
 * Handles historical order corrections per docs section 10.5 and BR-HIS-01 through BR-HIS-06.
 *
 * Allowed transitions:
 *   DELIVERED → SKIPPED  (with optional auto-refund if isSystemError=true)
 *   SKIPPED   → DELIVERED (auto DEBIT, negative balance permitted per BR-HIS-03)
 *   LOCKED    → CANCELLED (delivery_record retained, status→CANCELLED per BR-HIS-01)
 *
 * All other transitions → 409 INVALID_STATUS_TRANSITION.
 * Wallet ledger is append-only — corrections insert new entries, never modify existing ones (BR-HIS-06).
 */
@Service
@RequiredArgsConstructor
public class OrderCorrectionService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OrderRepository orderRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public OrderCorrectionResponse correctOrder(UUID orderId, OrderCorrectionRequest request, UUID adminId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Order not found: " + orderId, HttpStatus.NOT_FOUND));

        Order.OrderStatus currentStatus = order.getStatus();
        Order.OrderStatus newStatus;
        try {
            newStatus = Order.OrderStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Invalid target status: " + request.getStatus(), HttpStatus.CONFLICT);
        }

        OffsetDateTime now = OffsetDateTime.now(IST);
        boolean autoRefundIssued = false;

        // Route to the correct correction handler based on transition
        if (currentStatus == Order.OrderStatus.DELIVERED && newStatus == Order.OrderStatus.SKIPPED) {
            autoRefundIssued = handleDeliveredToSkipped(order, request, adminId, now);

        } else if (currentStatus == Order.OrderStatus.SKIPPED && newStatus == Order.OrderStatus.DELIVERED) {
            handleSkippedToDelivered(order, adminId, now);

        } else if (currentStatus == Order.OrderStatus.LOCKED && newStatus == Order.OrderStatus.CANCELLED) {
            handleLockedToCancelled(order, request, adminId, now);

        } else {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Transition from " + currentStatus + " to " + newStatus + " is not allowed",
                    HttpStatus.CONFLICT);
        }

        // Audit log — action_type: HISTORICAL_ORDER_EDIT (BR-AUD-01, BR-HIS-01)
        auditLogService.log("HISTORICAL_ORDER_EDIT", "order", orderId.toString(),
                java.util.Map.of("status", currentStatus.name()),
                java.util.Map.of("status", order.getStatus().name(), "autoRefundIssued", autoRefundIssued),
                adminId, request.getNotes());

        return OrderCorrectionResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus().name())
                .autoRefundIssued(autoRefundIssued)
                .updatedAt(now)
                .build();
    }

    /**
     * DELIVERED → SKIPPED
     * If isSystemError=true: insert REFUND ledger entry (source_type=HISTORICAL_CORRECTION) (BR-HIS-02)
     * If isSystemError=false: no automatic ledger entry (BR-HIS-04)
     */
    private boolean handleDeliveredToSkipped(Order order, OrderCorrectionRequest request,
                                              UUID adminId, OffsetDateTime now) {
        // skipReason is required when new status is SKIPPED
        Order.SkipReason skipReason = parseSkipReason(request.getSkipReason());

        order.setStatus(Order.OrderStatus.SKIPPED);
        order.setSkipReason(skipReason);
        orderRepository.save(order);

        DeliveryRecord record = requireDeliveryRecord(order.getId());
        record.setStatus(DeliveryRecord.DeliveryRecordStatus.SKIPPED);
        record.setSkipReason(skipReason);
        record.setDeliveredAt(null);
        deliveryRecordRepository.save(record);

        boolean isSystemError = Boolean.TRUE.equals(request.getIsSystemError());
        if (isSystemError) {
            // Auto-refund: reverse the original wallet deduction (BR-HIS-02)
            long currentBalance = getCurrentBalance(order.getCustomerId());
            long newBalance = currentBalance + order.getTotalAmountPaise();

            WalletLedger refundEntry = new WalletLedger();
            refundEntry.setCustomerId(order.getCustomerId());
            refundEntry.setOrderId(order.getId());
            refundEntry.setEntryType(WalletLedger.EntryType.REFUND);
            refundEntry.setSourceType(WalletLedger.SourceType.HISTORICAL_CORRECTION);
            refundEntry.setAmountPaise(order.getTotalAmountPaise());
            refundEntry.setRunningBalancePaise(newBalance);
            refundEntry.setDescription("Refund for system error correction on " + order.getDeliveryDate());
            refundEntry.setCreatedByUserId(adminId);
            walletLedgerRepository.save(refundEntry);

            return true;
        }

        return false;
    }

    /**
     * SKIPPED → DELIVERED
     * Treated as standard delivery confirmation — insert DEBIT ledger entry (BR-HIS-03).
     * Negative balance is permitted for this correction (BR-HIS-03).
     */
    private void handleSkippedToDelivered(Order order, UUID adminId, OffsetDateTime now) {
        order.setStatus(Order.OrderStatus.DELIVERED);
        order.setSkipReason(null);
        orderRepository.save(order);

        DeliveryRecord record = requireDeliveryRecord(order.getId());
        record.setStatus(DeliveryRecord.DeliveryRecordStatus.DELIVERED);
        record.setSkipReason(null);
        record.setDeliveredAt(now);
        deliveryRecordRepository.save(record);

        // Insert DEBIT — negative balance permitted (BR-HIS-03, no balance check)
        long currentBalance = getCurrentBalance(order.getCustomerId());
        long newBalance = currentBalance - order.getTotalAmountPaise();

        WalletLedger debitEntry = new WalletLedger();
        debitEntry.setCustomerId(order.getCustomerId());
        debitEntry.setOrderId(order.getId());
        debitEntry.setEntryType(WalletLedger.EntryType.DEBIT);
        debitEntry.setSourceType(WalletLedger.SourceType.HISTORICAL_CORRECTION_DEBIT);
        debitEntry.setAmountPaise(order.getTotalAmountPaise());
        debitEntry.setRunningBalancePaise(newBalance);
        debitEntry.setDescription("Historical correction delivery on " + order.getDeliveryDate());
        debitEntry.setCreatedByUserId(adminId);
        walletLedgerRepository.save(debitEntry);
    }

    /**
     * LOCKED → CANCELLED
     * delivery_record is retained and its status transitions to CANCELLED (BR-HIS-01).
     * No wallet ledger entry — order was never delivered.
     */
    private void handleLockedToCancelled(Order order, OrderCorrectionRequest request,
                                          UUID adminId, OffsetDateTime now) {
        order.setStatus(Order.OrderStatus.CANCELLED);

        // cancellationComment is only valid when new status is CANCELLED (API spec 10.5 notes)
        if (request.getCancellationComment() != null && !request.getCancellationComment().isBlank()) {
            order.setCancellationComment(request.getCancellationComment());
            order.setCancellationCommentedAt(now);
            order.setCancellationCommentedBy(adminId);
        }
        orderRepository.save(order);

        // Retain delivery_record, transition to CANCELLED (BR-HIS-01)
        deliveryRecordRepository.findByOrderId(order.getId()).ifPresent(record -> {
            record.setStatus(DeliveryRecord.DeliveryRecordStatus.CANCELLED);
            deliveryRecordRepository.save(record);
        });
    }

    private Order.SkipReason parseSkipReason(String skipReasonStr) {
        if (skipReasonStr == null || skipReasonStr.isBlank()) {
            throw new BusinessException("INVALID_SKIP_REASON",
                    "skipReason is required when status is SKIPPED", HttpStatus.BAD_REQUEST);
        }
        try {
            return Order.SkipReason.valueOf(skipReasonStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_SKIP_REASON",
                    "skipReason must be one of: CUSTOMER_UNAVAILABLE, PRODUCT_UNAVAILABLE, DAMAGED, OTHER",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private DeliveryRecord requireDeliveryRecord(UUID orderId) {
        return deliveryRecordRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Delivery record not found for order: " + orderId, HttpStatus.NOT_FOUND));
    }

    private long getCurrentBalance(UUID customerId) {
        return walletLedgerRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId)
                .map(WalletLedger::getRunningBalancePaise)
                .orElse(0L);
    }
}
