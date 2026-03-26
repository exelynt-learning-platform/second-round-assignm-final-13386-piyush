package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.user.UserProfileResponse;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.exception.UnauthorizedException;
import com.ecommerce.backend.mapper.UserMapper;
import com.ecommerce.backend.repository.UserRepository;
import com.ecommerce.backend.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getCurrentAuthenticatedUser() {
        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user no longer exists"));
    }

    public UserProfileResponse getCurrentProfile() {
        return UserMapper.toProfile(getCurrentAuthenticatedUser());
    }
}
