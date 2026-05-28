package com.juiceplatform.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class OrderListResponse {

    private UUID id;
    private UUID subscriptionId;
    private String productName;
    private int quantity;
    private long totalAmountPaise;
    private LocalDate deliveryDate;
    private String status;

    @JsonProperty("isLocked")
    private Boolean isLocked;
}
