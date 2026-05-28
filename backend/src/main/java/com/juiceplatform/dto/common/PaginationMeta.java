package com.juiceplatform.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaginationMeta {

    private int page;
    private int size;
    private long total;
}
