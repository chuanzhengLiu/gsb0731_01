package com.podcast.collab.security;

import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static CurrentUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser cu)) {
            throw new UnauthorizedException("未登录");
        }
        return cu;
    }

    public static Long currentUserId() {
        return currentUser().getUserId();
    }

    /** 所有业务查询必须以此 teamId 过滤，保证团队隔离 */
    public static Long currentTeamId() {
        CurrentUser cu = currentUser();
        if (cu.getTeamId() == null) {
            throw new ForbiddenException("当前用户不属于任何团队");
        }
        return cu.getTeamId();
    }

    public static void requireRole(String... roles) {
        String role = currentUser().getRole();
        for (String r : roles) {
            if (r.equals(role)) {
                return;
            }
        }
        throw new ForbiddenException("权限不足");
    }
}
