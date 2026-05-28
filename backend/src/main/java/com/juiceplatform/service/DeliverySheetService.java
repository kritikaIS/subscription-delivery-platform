package com.juiceplatform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juiceplatform.dto.deliverysheet.DeliverySheetOrderEntry;
import com.juiceplatform.dto.deliverysheet.DeliverySheetResponse;
import com.juiceplatform.dto.deliverysheet.JuiceSummaryEntry;
import com.juiceplatform.entity.DeliveryRecord;
import com.juiceplatform.entity.DeliverySheetSnapshot;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.DeliveryRecordRepository;
import com.juiceplatform.repository.DeliverySheetSnapshotRepository;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.ProductRepository;
import com.juiceplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates and retrieves delivery sheet snapshots.
 * Per docs: snapshot is replaced on rerun (not append-only).
 * Per API spec Domain 13: snapshot_json stores delivery list + juiceSummary.
 * Per docs: only LOCKED orders with PENDING delivery_records appear.
 * CANCELLED delivery_records are excluded (BR-HIS-01, API spec Domain 10 note).
 */
@Service
@RequiredArgsConstructor
public class DeliverySheetService {

    private static final Logger log = LoggerFactory.getLogger(DeliverySheetService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DeliverySheetSnapshotRepository snapshotRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generates (or regenerates) the delivery sheet snapshot for the given date.
     * On rerun: replaces the existing snapshot (DELETE + INSERT per db-schema §3.13 notes).
     * Idempotent: safe to call multiple times for the same date.
     *
     * @param deliveryDate the target delivery date
     * @param source       SCHEDULER or ADMIN_RERUN
     * @param adminId      null for scheduler runs, admin UUID for manual reruns
     */
    @Transactional
    public DeliverySheetResponse generateSnapshot(LocalDate deliveryDate,
                                                   DeliverySheetSnapshot.GeneratedBySource source,
                                                   UUID adminId) {
        log.info("DeliverySheetGenerationJob starting for delivery date: {} (source: {})", deliveryDate, source);

        // Build the delivery sheet data from LOCKED orders with PENDING delivery_records
        DeliverySheetResponse sheetData = buildSheetData(deliveryDate);

        // Serialize to JSON for storage
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(sheetData);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize delivery sheet snapshot", e);
        }

        // Replace existing snapshot if present (per db-schema §3.13: DELETE + INSERT on rerun)
        snapshotRepository.findByDeliveryDate(deliveryDate)
                .ifPresent(snapshotRepository::delete);
        snapshotRepository.flush();

        DeliverySheetSnapshot snapshot = new DeliverySheetSnapshot();
        snapshot.setDeliveryDate(deliveryDate);
        snapshot.setGeneratedAt(OffsetDateTime.now(IST));
        snapshot.setGeneratedBySource(source);
        snapshot.setGeneratedByUserId(adminId);
        snapshot.setSnapshotJson(snapshotJson);
        snapshotRepository.save(snapshot);

        log.info("DeliverySheetGenerationJob completed for {}: {} orders in sheet", deliveryDate, sheetData.getOrders().size());

        return sheetData;
    }

    /**
     * Retrieves the delivery sheet snapshot for a given date.
     * Returns 404 if no snapshot exists (not yet generated).
     */
    @Transactional(readOnly = true)
    public DeliverySheetResponse getSnapshot(LocalDate deliveryDate) {
        DeliverySheetSnapshot snapshot = snapshotRepository.findByDeliveryDate(deliveryDate)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "No delivery sheet exists for date: " + deliveryDate, HttpStatus.NOT_FOUND));

        try {
            return objectMapper.readValue(snapshot.getSnapshotJson(), DeliverySheetResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize delivery sheet snapshot", e);
        }
    }

    /**
     * Builds the delivery sheet data from LOCKED orders with PENDING delivery_records.
     * CANCELLED delivery_records are excluded per API spec Domain 10 note and BR-HIS-01.
     */
    private DeliverySheetResponse buildSheetData(LocalDate deliveryDate) {
        // Find all LOCKED orders for this delivery date
        List<Order> lockedOrders = orderRepository.findByDeliveryDateAndStatus(
                deliveryDate, Order.OrderStatus.LOCKED);

        List<DeliverySheetOrderEntry> orderEntries = new ArrayList<>();
        Map<String, Integer> juiceTotals = new LinkedHashMap<>();

        for (Order order : lockedOrders) {
            // Only include orders with PENDING delivery_records (exclude CANCELLED)
            DeliveryRecord record = deliveryRecordRepository.findByOrderId(order.getId())
                    .orElse(null);
            if (record == null || record.getStatus() == DeliveryRecord.DeliveryRecordStatus.CANCELLED) {
                continue;
            }

            // Load customer for name and phone
            User customer = userRepository.findById(order.getCustomerId()).orElse(null);
            String customerName = customer != null ? customer.getName() : "Unknown";
            String phone = customer != null ? customer.getPhone() : "";

            // Format address per API spec: "line1, line2, city pincode"
            String address = formatAddress(order);

            // Load product name
            String productName = productRepository.findById(order.getProductId())
                    .map(Product::getName)
                    .orElse("Unknown Product");

            orderEntries.add(DeliverySheetOrderEntry.builder()
                    .orderId(order.getId())
                    .customerName(customerName)
                    .phone(phone != null ? phone : "")
                    .address(address)
                    .deliveryNotes(order.getDeliveryNotes())
                    .productName(productName)
                    .quantity(order.getQuantity())
                    .build());

            // Aggregate juice summary
            juiceTotals.merge(productName, order.getQuantity(), Integer::sum);
        }

        List<JuiceSummaryEntry> juiceSummary = juiceTotals.entrySet().stream()
                .map(e -> JuiceSummaryEntry.builder()
                        .productName(e.getKey())
                        .totalQuantity(e.getValue())
                        .build())
                .toList();

        return DeliverySheetResponse.builder()
                .deliveryDate(deliveryDate)
                .generatedAt(OffsetDateTime.now(IST))
                .orders(orderEntries)
                .juiceSummary(juiceSummary)
                .build();
    }

    /**
     * Formats address as a display string per API spec Domain 13 notes:
     * "line1, line2, city pincode"
     */
    private String formatAddress(Order order) {
        StringBuilder sb = new StringBuilder();
        if (order.getDeliveryLine1() != null) sb.append(order.getDeliveryLine1());
        if (order.getDeliveryLine2() != null && !order.getDeliveryLine2().isBlank()) {
            sb.append(", ").append(order.getDeliveryLine2());
        }
        if (order.getDeliveryCity() != null) sb.append(", ").append(order.getDeliveryCity());
        if (order.getDeliveryPincode() != null) sb.append(" ").append(order.getDeliveryPincode());
        return sb.toString();
    }
}
