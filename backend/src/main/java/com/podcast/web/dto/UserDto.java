package com.podcast.web.dto;

import com.podcast.domain.User;

public record UserDto(
        Long id,
        String email,
        String name,
        String role,
        Long teamId
) {
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getName(), u.getRole().name(), u.getTeamId());
    }
}
