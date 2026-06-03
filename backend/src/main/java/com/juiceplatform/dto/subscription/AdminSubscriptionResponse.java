package com.juiceplatform.dto.subscription;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class AdminSubscriptionResponse {

    private UUID id;
    private UUID customerId;
    private String customerName;
    private UUID productId;
    private String productName;
    private Integer quantity;
    private String status;
    private String pauseReason;
    private LocalDate startDate;
    private OffsetDateTime createdAt;
}
