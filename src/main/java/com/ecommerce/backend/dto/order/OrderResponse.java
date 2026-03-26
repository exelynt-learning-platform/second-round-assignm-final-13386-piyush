package com.ecommerce.backend.dto.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        BigDecimal totalPrice,
        ShippingDetailsResponse shippingDetails,
        List<OrderItemResponse> items,
        String paymentSessionId,
        Instant createdAt
) {
}
