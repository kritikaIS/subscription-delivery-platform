package com.juiceplatform.dto.holiday;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class DeleteHolidayResponse {

    private UUID id;
    private boolean deleted;
}
