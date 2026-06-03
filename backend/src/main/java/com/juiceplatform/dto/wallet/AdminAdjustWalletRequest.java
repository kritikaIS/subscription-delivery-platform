package com.juiceplatform.dto.wallet;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public record AdminAdjustWalletRequest(
        @NotNull(message = "Adjustment amount cannot be null")
        Long amountPaise,

        @NotBlank(message = "Reason is required for manual adjustments")
        String reason
) {}