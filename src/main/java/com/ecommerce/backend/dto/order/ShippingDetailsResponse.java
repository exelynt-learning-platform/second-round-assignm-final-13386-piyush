package com.ecommerce.backend.dto.order;

public record ShippingDetailsResponse(
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        String phone
) {
}
