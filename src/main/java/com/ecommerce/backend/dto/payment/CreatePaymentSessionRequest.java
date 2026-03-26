package com.ecommerce.backend.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentSessionRequest(
        @NotNull(message = "Order id is required")
        Long orderId,

        @NotBlank(message = "Success URL is required")
        String successUrl,

        @NotBlank(message = "Cancel URL is required")
        String cancelUrl
) {
}
