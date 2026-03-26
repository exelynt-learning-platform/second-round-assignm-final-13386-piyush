package com.ecommerce.backend.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShippingDetailsRequest(
        @NotBlank(message = "Address line 1 is required")
        @Size(max = 255, message = "Address line 1 is too long")
        String addressLine1,

        @Size(max = 255, message = "Address line 2 is too long")
        String addressLine2,

        @NotBlank(message = "City is required")
        @Size(max = 120, message = "City is too long")
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 120, message = "State is too long")
        String state,

        @NotBlank(message = "Postal code is required")
        @Size(max = 20, message = "Postal code is too long")
        String postalCode,

        @NotBlank(message = "Country is required")
        @Size(max = 120, message = "Country is too long")
        String country,

        @Size(max = 30, message = "Phone is too long")
        String phone
) {
}
