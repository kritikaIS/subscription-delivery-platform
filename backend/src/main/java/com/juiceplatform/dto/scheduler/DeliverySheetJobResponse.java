package com.juiceplatform.dto.scheduler;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Builder
public class DeliverySheetJobResponse {

    private String job;
    private String status;
    private LocalDate targetDate;
    private OffsetDateTime ranAt;
}
