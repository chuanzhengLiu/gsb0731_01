package com.podcast.collab.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static CurrentUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser cu) {
            return cu;
        }
        return null;
    }

    public static CurrentUser requireUser() {
        CurrentUser cu = currentUser();
        if (cu == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        return cu;
    }

    public static Long requireTeamId() {
        CurrentUser cu = requireUser();
        if (cu.teamId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("No active team selected");
        }
        return cu.teamId();
    }
}
