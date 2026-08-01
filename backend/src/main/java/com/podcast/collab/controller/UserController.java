package com.podcast.collab.controller;

import com.podcast.collab.dto.common.ApiResponse;
import com.podcast.collab.entity.User;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.UserRepository;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public UserController(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserProfileResponse response = new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getAvatarUrl(),
                user.getSystemRole().name()
        );
        return ApiResponse.ok(response);
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateCurrentUser(@Valid @RequestBody UpdateProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        user = userRepository.save(user);

        auditService.log(userId, "UPDATE_PROFILE", "User", userId, "User profile updated");

        UserProfileResponse response = new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getAvatarUrl(),
                user.getSystemRole().name()
        );
        return ApiResponse.ok(response);
    }

    @Data
    public static class UpdateProfileRequest {

        @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
        private String name;

        private String avatarUrl;
    }

    public record UserProfileResponse(
            Long id,
            String email,
            String name,
            String avatarUrl,
            String systemRole
    ) {}
}
