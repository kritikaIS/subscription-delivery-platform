package com.juiceplatform.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiErrorResponse {

    private boolean success;
    private ErrorDetail error;

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, new ErrorDetail(code, message));
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorDetail {
        private String code;
        private String message;
    }
}
