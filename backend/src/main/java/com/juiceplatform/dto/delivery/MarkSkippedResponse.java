package com.juiceplatform.dto.delivery;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class MarkSkippedResponse {

    private UUID orderId;
    private String status;
    private String skipReason;
    private OffsetDateTime skippedAt;
}
