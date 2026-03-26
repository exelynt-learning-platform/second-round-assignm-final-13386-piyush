package com.ecommerce.backend.exception;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Builder
public record ApiError(
        Instant timestamp,
        boolean success,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors
) {
}
