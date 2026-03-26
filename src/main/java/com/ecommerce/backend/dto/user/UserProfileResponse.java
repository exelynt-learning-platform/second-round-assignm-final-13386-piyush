package com.ecommerce.backend.dto.user;

public record UserProfileResponse(
        Long id,
        String fullName,
        String email,
        String role
) {
}
