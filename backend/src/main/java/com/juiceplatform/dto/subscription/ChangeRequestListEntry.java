package com.juiceplatform.dto.subscription;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class ChangeRequestListEntry {

    private UUID id;
    private String type;

    /** Populated when type = QUANTITY; null otherwise. */
    private Integer newQuantity;

    /** Populated when type = PRODUCT; null otherwise. */
    private UUID newProductId;

    /** Populated when type = PRODUCT; null otherwise. */
    private String newProductName;

    private String status;
    private LocalDate effectiveDate;
    private String requestedBy;
    private OffsetDateTime createdAt;
}
