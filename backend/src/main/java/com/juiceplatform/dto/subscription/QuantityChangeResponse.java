package com.juiceplatform.dto.subscription;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class QuantityChangeResponse {

    private UUID changeRequestId;
    private String type;
    private int newQuantity;
    private String status;
    private LocalDate effectiveDate;
}
