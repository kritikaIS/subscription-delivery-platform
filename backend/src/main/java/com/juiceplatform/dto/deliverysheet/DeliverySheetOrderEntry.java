package com.juiceplatform.dto.deliverysheet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One order entry in the delivery sheet.
 * Matches the API spec Domain 13 response format exactly.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliverySheetOrderEntry {

    private UUID orderId;
    private String customerName;
    private String phone;
    private String address;
    private String deliveryNotes;
    private String productName;
    private int quantity;
}
