package com.juiceplatform.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class EnableProductResponse {

    private UUID productId;

    @JsonProperty("isAvailable")
    private Boolean isAvailable;

    private OffsetDateTime enabledAt;
}
