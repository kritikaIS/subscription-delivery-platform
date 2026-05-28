package com.juiceplatform.dto.delivery;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderCorrectionRequest {

    @NotBlank
    private String status;

    private String skipReason;

    private Boolean isSystemError;

    private String notes;

    private String cancellationComment;
}
