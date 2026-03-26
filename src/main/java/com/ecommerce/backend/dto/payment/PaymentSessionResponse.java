package com.ecommerce.backend.dto.payment;

public record PaymentSessionResponse(
        String sessionId,
        String checkoutUrl,
        Long orderId,
        String status
) {
}
