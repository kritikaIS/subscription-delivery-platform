package com.juiceplatform.dto.subscription;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class SubscriptionDetailResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private int quantity;
    private String status;
    private LocalDate effectiveStartDate;

    /**
     * APPROVED change requests for this subscription.
     * Shown in the subscription detail view per API spec 4.3.
     */
    private List<PendingChangeEntry> pendingChangeRequests;

    private OffsetDateTime createdAt;

    @Getter
    @Builder
    public static class PendingChangeEntry {
        private String type;
        private Integer newQuantity;
        private UUID newProductId;
        private String newProductName;
        private String status;
        private LocalDate effectiveDate;
    }
}
