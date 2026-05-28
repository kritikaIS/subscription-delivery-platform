package com.juiceplatform.dto.delivery;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class OrderCorrectionResponse {

    private UUID orderId;
    private String status;
    private boolean autoRefundIssued;
    private OffsetDateTime updatedAt;
}
