package com.juiceplatform.dto.scheduler;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Builder
public class FreezeJobResponse {

    private String job;
    private String status;
    private LocalDate targetDate;
    private int ordersLocked;
    private OffsetDateTime ranAt;
}
