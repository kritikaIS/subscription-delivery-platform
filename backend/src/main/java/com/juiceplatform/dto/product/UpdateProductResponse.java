package com.juiceplatform.dto.product;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class UpdateProductResponse {

    private UUID id;
    private String name;
    private long pricePerUnitPaise;
    private OffsetDateTime updatedAt;
}
