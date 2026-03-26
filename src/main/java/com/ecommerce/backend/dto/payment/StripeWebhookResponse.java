package com.ecommerce.backend.dto.payment;

public record StripeWebhookResponse(
        String eventType,
        String status,
        String message
) {
}
