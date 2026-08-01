package com.podcast.web.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserDto user
) {}
