package com.juiceplatform.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PagedResponse<T> {

    private List<T> items;
}
