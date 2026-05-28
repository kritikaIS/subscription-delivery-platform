package com.juiceplatform.dto.wallet;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class AdminCreditResponse {

    private UUID ledgerEntryId;
    private String entryType;
    private String sourceType;
    private long amountPaise;
    private long newBalancePaise;
    private String notes;
    private OffsetDateTime createdAt;
}
