package com.juiceplatform.dto.wallet;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class RechargeRequestResponse {

    private String status;
    private OffsetDateTime requestedAt;
}
