package com.juiceplatform.dto.order;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class AdminOrderResponse {

    private UUID id;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private UUID subscriptionId;
    private UUID productId;
    private String productName;
    private Integer quantity;
    private Long amountPaise;
    private String status;
    private LocalDate deliveryDate;
    private String deliveryAddress;
    private String deliveryNotes;
}
