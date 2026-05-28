package com.juiceplatform.dto.scheduler;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class SchedulerJobHistoryEntry {

    private UUID id;
    private String jobName;
    private String status;
    private LocalDate targetDate;
    private Integer rowsProcessed;
    private String errorMessage;
    private OffsetDateTime ranAt;
}
