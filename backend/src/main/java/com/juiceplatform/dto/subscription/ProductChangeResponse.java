package com.juiceplatform.dto.subscription;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class ProductChangeResponse {

    private UUID changeRequestId;
    private String type;
    private UUID newProductId;
    private String newProductName;
    private String status;
    private LocalDate effectiveDate;
}
