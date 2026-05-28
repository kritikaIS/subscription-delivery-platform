package com.juiceplatform.dto.holiday;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AddHolidayRequest {

    @NotNull
    private LocalDate date;

    @NotBlank
    @Size(max = 100)
    private String name;
}
