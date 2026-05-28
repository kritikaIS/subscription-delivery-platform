package com.juiceplatform.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class ProductResponse {

    private UUID id;
    private String name;
    private String description;
    private long pricePerUnitPaise;
    private String unitLabel;
    private String category;

    @JsonProperty("isAvailable")
    private Boolean isAvailable;

    private String imageUrl;
    private OffsetDateTime createdAt;
}
