package com.juiceplatform.dto.wallet;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreditRequest {

    @NotNull
    @Min(value = 100, message = "Minimum wallet credit amount is ₹1 (100 paise)")
    private Long amountPaise;

    private String notes;
}
