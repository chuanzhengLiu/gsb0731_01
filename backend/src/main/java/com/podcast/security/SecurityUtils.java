package com.podcast.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience accessor for the authenticated principal. */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static UserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        return null;
    }

    public static Long currentUserId() {
        UserPrincipal p = currentUser();
        return p != null ? p.getUserId() : null;
    }

    public static Long currentTeamId() {
        UserPrincipal p = currentUser();
        return p != null ? p.getTeamId() : null;
    }
}
