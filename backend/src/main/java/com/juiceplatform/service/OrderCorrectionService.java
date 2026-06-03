package com.juiceplatform.service;

import com.juiceplatform.dto.delivery.AdminOrderOverrideRequest;
import com.juiceplatform.dto.delivery.OrderCorrectionRequest;
import com.juiceplatform.dto.delivery.OrderCorrectionResponse;
import com.juiceplatform.entity.DeliveryRecord;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.WalletLedger;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.DeliveryRecordRepository;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.ProductRepository;
import com.juiceplatform.repository.WalletLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles historical order corrections per docs section 10.5 and BR-HIS-01 through BR-HIS-06.
 *
 * Allowed transitions:
 * DELIVERED → SKIPPED  (with optional auto-refund if isSystemError=true)
 * SKIPPED   → DELIVERED (auto DEBIT, negative balance permitted per BR-HIS-03)
 * LOCKED    → CANCELLED (delivery_record retained, status→CANCELLED per BR-HIS-01)
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
    private final ProductRepository productRepository; // Injected for order overrides
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
     * Overrides a LOCKED order prior to delivery (BR-HIS-05).
     * Modifies quantity, product, and/or address, dynamically recalculating the total amount.
     */
    @Transactional
    public OrderCorrectionResponse overrideOrder(UUID orderId, AdminOrderOverrideRequest request, UUID adminId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));

        // Ensure the order hasn't been delivered/skipped/cancelled yet
        if (order.getStatus() == Order.OrderStatus.DELIVERED ||
                order.getStatus() == Order.OrderStatus.SKIPPED ||
                order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new BusinessException("INVALID_STATE", "Cannot override an order that has already been processed or cancelled", HttpStatus.BAD_REQUEST);
        }

        // Capture old state securely using HashMap to avoid NullPointerExceptions on Map.of()
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("quantity", order.getQuantity());
        oldValues.put("productId", order.getProductId() != null ? order.getProductId().toString() : "");
        oldValues.put("deliveryAddress", order.getDeliveryLine1() != null ? order.getDeliveryLine1() : "");
        oldValues.put("totalAmountPaise", order.getTotalAmountPaise());

        boolean isModified = false;

        // 1. Update Product & recalculate Unit Price
        if (request.productId() != null && !request.productId().equals(order.getProductId())) {
            Product newProduct = productRepository.findById(request.productId())
                    .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));

            order.setProductId(newProduct.getId());
            order.setUnitPricePaise(newProduct.getPricePerUnitPaise());
            isModified = true;
        }

        // 2. Update Quantity
        if (request.quantity() != null && !request.quantity().equals(order.getQuantity())) {
            order.setQuantity(request.quantity());
            isModified = true;
        }

        // 3. Update Delivery Address
        if (request.deliveryAddress() != null && !request.deliveryAddress().equals(order.getDeliveryLine1())) {
            order.setDeliveryLine1(request.deliveryAddress());
            isModified = true;
        }

        // 4. Save and Log if changes occurred
        if (isModified) {
            // Dynamic recalculation of total (BR-HIS-05)
            order.setTotalAmountPaise((long) order.getUnitPricePaise() * order.getQuantity());
            order = orderRepository.save(order);

            Map<String, Object> newValues = new HashMap<>();
            newValues.put("quantity", order.getQuantity());
            newValues.put("productId", order.getProductId() != null ? order.getProductId().toString() : "");
            newValues.put("deliveryAddress", order.getDeliveryLine1() != null ? order.getDeliveryLine1() : "");
            newValues.put("totalAmountPaise", order.getTotalAmountPaise());

            auditLogService.log("ORDER_OVERRIDE", "order", order.getId().toString(),
                    oldValues, newValues, adminId, request.reason());
        }

        return OrderCorrectionResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus().name())
                .autoRefundIssued(false) // Not applicable for pre-delivery overrides
                .updatedAt(OffsetDateTime.now(IST))
                .build();
    }

    /**
     * DELIVERED → SKIPPED
     * If isSystemError=true: insert REFUND ledger entry (source_type=HISTORICAL_CORRECTION) (BR-HIS-02)
     * If isSystemError=false: no automatic ledger entry (BR-HIS-04)
     */
    private boolean handleDeliveredToSkipped(Order order, OrderCorrectionRequest request,
                                             UUID adminId, OffsetDateTime now) {
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
            long currentBalance = walletLedgerRepository.findTopByCustomerIdForUpdate(order.getCustomerId())
                    .map(WalletLedger::getRunningBalancePaise)
                    .orElse(0L);
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

        long currentBalance = walletLedgerRepository.findTopByCustomerIdForUpdate(order.getCustomerId())
                .map(WalletLedger::getRunningBalancePaise)
                .orElse(0L);
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

        if (request.getCancellationComment() != null && !request.getCancellationComment().isBlank()) {
            order.setCancellationComment(request.getCancellationComment());
            order.setCancellationCommentedAt(now);
            order.setCancellationCommentedBy(adminId);
        }
        orderRepository.save(order);

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
}