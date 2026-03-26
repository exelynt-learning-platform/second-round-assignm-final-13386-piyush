package com.ecommerce.backend.dto.payment;

public record PaymentStatusResponse(
        Long orderId,
        String status,
        String message
) {
}
