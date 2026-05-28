package com.juiceplatform.dto.scheduler;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Builder
public class GenerateJobResponse {

    private String job;
    private String status;
    private LocalDate targetDate;
    private int ordersGenerated;
    private int subscriptionsActivated;
    private int changeRequestsApplied;
    private OffsetDateTime ranAt;
}
