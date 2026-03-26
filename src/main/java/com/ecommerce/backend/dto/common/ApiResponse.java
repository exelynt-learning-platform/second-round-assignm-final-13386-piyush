package com.ecommerce.backend.dto.common;

import java.time.Instant;

public record ApiResponse<T>(
        String status,
        String message,
        T data,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("success", message, data, Instant.now());
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>("success", message, null, Instant.now());
    }
}
