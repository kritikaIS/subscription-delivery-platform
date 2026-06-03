package com.juiceplatform.dto.delivery;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record AdminOrderOverrideRequest(
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        UUID productId,

        String deliveryAddress,

        @NotBlank(message = "Reason is required for order overrides")
        String reason
) {}