package com.juiceplatform.dto.delivery;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MarkSkippedRequest {

    @NotBlank
    private String skipReason;
}
