package com.juiceplatform.dto.subscription;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChangeProductRequest {

    @NotNull(message = "newProductId is required")
    private UUID newProductId;
}
