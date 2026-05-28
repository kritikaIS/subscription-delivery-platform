package com.juiceplatform.dto.wallet;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class LedgerEntryResponse {

    private UUID id;
    private String entryType;
    private String sourceType;
    private long amountPaise;
    private long balanceAfterPaise;
    private String description;
    private OffsetDateTime createdAt;
}
