package com.spendly.backend.common;

import java.util.List;

public record ApiError(String code, String message, List<Detail> details) {
    public record Detail(String field, String message) { }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }
}
