package com.podcast.collab.service;

import com.podcast.collab.entity.AuditLog;
import com.podcast.collab.repository.AuditLogRepository;
import com.podcast.collab.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 操作审计：音频上传、标注增删、版本切换、分发状态变更等 */
@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final ClientIpResolver clientIpResolver;

    public void log(Long userId, Long teamId, String action, String targetType, Long targetId, String details) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setTeamId(teamId);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetails(details);
        entry.setIpAddress(clientIp());
        auditLogRepository.save(entry);
    }

    private String clientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            return clientIpResolver.resolve(request);
        } catch (Exception e) {
            return null;
        }
    }
}
