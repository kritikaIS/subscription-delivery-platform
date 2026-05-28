package com.juiceplatform.dto.holiday;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class HolidayResponse {

    private UUID id;
    private LocalDate date;
    private String name;
    private OffsetDateTime createdAt;
}
