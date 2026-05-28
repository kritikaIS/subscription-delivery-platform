package com.juiceplatform.dto.deliverysheet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Delivery sheet response matching API spec Domain 13 exactly.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliverySheetResponse {

    private LocalDate deliveryDate;
    private OffsetDateTime generatedAt;
    private List<DeliverySheetOrderEntry> orders;
    private List<JuiceSummaryEntry> juiceSummary;
}
