package com.ecommerce.backend.mapper;

import com.ecommerce.backend.dto.user.UserProfileResponse;
import com.ecommerce.backend.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
