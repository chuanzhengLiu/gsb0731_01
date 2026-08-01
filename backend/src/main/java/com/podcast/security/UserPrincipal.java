package com.podcast.security;

import java.security.Principal;

/** Authenticated principal carrying identity + team scope for isolation checks. */
public class UserPrincipal implements Principal {

    private final Long userId;
    private final String email;
    private final String role;
    private final Long teamId;

    public UserPrincipal(Long userId, String email, String role, Long teamId) {
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.teamId = teamId;
    }

    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public Long getTeamId() { return teamId; }

    @Override
    public String getName() { return email; }
}
