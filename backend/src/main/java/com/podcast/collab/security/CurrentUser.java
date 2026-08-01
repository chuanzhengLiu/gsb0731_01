package com.podcast.collab.security;

import com.podcast.collab.domain.Enums;

import java.util.Set;

public record CurrentUser(Long id, String email, String name, Long teamId, Enums.TeamRole teamRole) {

    private static final Set<Enums.TeamRole> PRODUCER_ROLES = Set.of(
            Enums.TeamRole.ADMIN, Enums.TeamRole.PRODUCER);

    public boolean isProducerOrAbove() {
        return PRODUCER_ROLES.contains(teamRole);
    }

    public boolean isAdmin() {
        return teamRole == Enums.TeamRole.ADMIN;
    }

    public boolean isOperator() {
        return teamRole == Enums.TeamRole.OPERATOR || isAdmin();
    }
}
