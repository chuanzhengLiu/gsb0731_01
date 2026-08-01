package com.podcast.collab.security;

import com.podcast.collab.exception.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static CustomUserDetails getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AccessDeniedException("No authenticated user found");
        }
        return userDetails;
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
