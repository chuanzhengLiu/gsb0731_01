package com.podcast.collab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcast.collab.domain.AuditLog;
import com.podcast.collab.repo.AuditLogRepository;
import com.podcast.collab.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

@Service
public class AuditService {

    private final AuditLogRepository auditRepo;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditRepo, ObjectMapper objectMapper) {
        this.auditRepo = auditRepo;
        this.objectMapper = objectMapper;
    }

    @Async
    @Transactional
    public void log(CurrentUser user, String action, String targetType, Object targetId, Object details) {
        AuditLog log = new AuditLog();
        log.setUserId(user != null ? user.id() : null);
        log.setTeamId(user != null ? user.teamId() : null);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId != null ? String.valueOf(targetId) : null);
        if (details != null) {
            try {
                log.setDetails(objectMapper.writeValueAsString(details));
            } catch (JsonProcessingException ignored) {
                log.setDetails(null);
            }
        }
        log.setIpAddress(currentIp());
        log.setCreatedAt(Instant.now());
        auditRepo.save(log);
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String ip = req.getHeader("X-Forwarded-For");
                if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
