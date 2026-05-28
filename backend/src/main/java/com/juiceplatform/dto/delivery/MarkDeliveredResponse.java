package com.juiceplatform.dto.delivery;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class MarkDeliveredResponse {

    private UUID orderId;
    private String status;
    private long amountDeductedPaise;
    private long newWalletBalancePaise;
    private OffsetDateTime deliveredAt;
}
