package com.juiceplatform.dto.product;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ProductCustomerResponse {

    private UUID id;
    private String name;
    private String description;
    private long pricePerUnitPaise;
    private String unitLabel;
    private String category;
    private String imageUrl;
}
