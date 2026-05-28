package com.juiceplatform.dto.subscription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChangeQuantityRequest {

    @NotNull(message = "newQuantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer newQuantity;
}
