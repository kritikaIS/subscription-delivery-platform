package com.juiceplatform.dto.subscription;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class PauseSubscriptionResponse {

    private UUID subscriptionId;
    private String status;
    private LocalDate pauseEffectiveDate;
}
