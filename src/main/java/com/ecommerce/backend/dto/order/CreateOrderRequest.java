package com.ecommerce.backend.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull(message = "Shipping details are required")
        @Valid
        ShippingDetailsRequest shippingDetails
) {
}
