package com.juiceplatform.dto.subscription;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class CancelSubscriptionResponse {

    private UUID subscriptionId;
    private String status;
    private LocalDate cancelEffectiveDate;
    private OffsetDateTime cancelledAt;
}
