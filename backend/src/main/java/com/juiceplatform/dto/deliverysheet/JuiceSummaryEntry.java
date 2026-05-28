package com.juiceplatform.dto.deliverysheet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Aggregated product quantity for the delivery sheet juice summary.
 * Matches the API spec Domain 13 juiceSummary format exactly.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JuiceSummaryEntry {

    private String productName;
    private int totalQuantity;
}
