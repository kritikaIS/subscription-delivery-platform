package com.juiceplatform.dto.wallet;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AdminSetBalanceRequest(
        @NotNull(message = "Target balance cannot be null")
        @Min(value = 0, message = "Target balance cannot be negative")
        Long targetBalancePaise,

        @NotBlank(message = "Reason is required for forcing a balance")
        String reason
) {}