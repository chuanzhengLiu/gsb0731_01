package com.podcast.service;

import com.podcast.domain.AuditLog;
import com.podcast.repository.AuditLogRepository;
import com.podcast.security.SecurityUtils;
import com.podcast.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records auditable actions (README §3.2): audio uploads, marker add/delete,
 * version switch, distribution status changes, etc.
 */
@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void log(String action, String targetType, Long targetId, String details) {
        UserPrincipal user = SecurityUtils.currentUser();
        AuditLog entry = new AuditLog();
        if (user != null) {
            entry.setUserId(user.getUserId());
            entry.setTeamId(user.getTeamId());
        }
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetails(details);
        entry.setIpAddress(clientIp());
        repo.save(entry);
    }

    private String clientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String xff = req.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                    return xff.split(",")[0].trim();
                }
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {
            // best-effort
        }
        return null;
    }
}
