package com.podcast.collab.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthDtos() {

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 10, max = 100) String password,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 120) String teamName
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record ForgotPasswordRequest(@Email @NotBlank String email) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, max = 100) String password
    ) {}

    public record AcceptInviteRequest(
            @NotBlank String token,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(min = 10, max = 100) String password
    ) {}

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserResponse user
    ) {}

    public record UserResponse(
            Long id,
            String email,
            String name,
            Long activeTeamId,
            String roleInTeam
    ) {}
}
