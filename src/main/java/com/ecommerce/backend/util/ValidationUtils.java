package com.ecommerce.backend.util;

import com.ecommerce.backend.exception.BadRequestException;

public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new BadRequestException(message);
        }
        return value;
    }

    public static String requireNonBlank(String value, String message) {
        if (isBlank(value)) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    public static int requirePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new BadRequestException(message);
        }
        return value;
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
