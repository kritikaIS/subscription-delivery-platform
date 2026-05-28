package com.juiceplatform.dto.subscription;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class SubscriptionResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private int quantity;
    private String status;
    private LocalDate effectiveStartDate;
    private OffsetDateTime createdAt;
}
