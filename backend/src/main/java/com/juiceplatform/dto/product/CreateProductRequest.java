package com.juiceplatform.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    private String description;

    @Positive
    private long pricePerUnitPaise;

    @Size(max = 20)
    private String unitLabel;

    @Size(max = 50)
    private String category;

    private String imageUrl;
}
