package com.podcast.collab.dto;

import com.podcast.collab.domain.Enums;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record TeamDtos() {

    public record TeamResponse(Long id, String name, Long createdBy, Instant createdAt, String myRole) {}

    public record UpdateTeamRequest(@NotBlank @Size(max = 120) String name) {}

    public record MemberResponse(Long userId, String email, String name, Enums.TeamRole role, Instant joinedAt) {}

    public record InviteRequest(
            @Email @NotBlank String email,
            @NotNull Enums.TeamRole role
    ) {}

    public record InvitationResponse(Long id, String email, Enums.TeamRole role, Instant expiresAt,
                                     boolean accepted, String inviteLink) {}

    public record UpdateRoleRequest(@NotNull Enums.TeamRole role) {}
}
