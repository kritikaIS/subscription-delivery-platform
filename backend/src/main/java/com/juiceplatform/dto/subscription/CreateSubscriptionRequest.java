package com.juiceplatform.dto.subscription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubscriptionRequest {

    @NotNull
    private UUID productId;

    @Min(1)
    private int quantity;
}
